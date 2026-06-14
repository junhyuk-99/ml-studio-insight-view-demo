import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { anomalyCauseService } from '../services/anomalyCauseService';
import { modelTrainService } from '../services/modelTrainService';
import type {
  AnomalyCauseDetail,
  AnomalyCauseListItem,
  AnomalyCauseListResponse,
  AnomalyCauseRun,
  CauseCandidate,
  GroupScore,
} from '../types/anomalyCause';
import type { AnomalyRunOption, FeatureDataset } from '../types/modelTrain';
import type { OperationDatasetOption } from '../utils/operationDataset';
import {
  extractDatasetKeyFromRouteState,
  persistOperationDatasetKey,
  readPersistedOperationDatasetKey,
  resolveOperationDatasetKeyWithFallback,
} from '../utils/operationDataset';

const DEFAULT_PAGE_SIZE = 12;
const DEFAULT_LIST_RESPONSE: AnomalyCauseListResponse = {
  items: [],
  total: 0,
  page: 0,
  size: DEFAULT_PAGE_SIZE,
  totalPages: 0,
};

const LOAD_ERROR_MESSAGE = 'AI 원인 분석 데이터를 불러오지 못했습니다.';

const STATUS_OPTIONS = [
  { value: 'ALL', label: '전체' },
  { value: 'NORMAL', label: 'NORMAL' },
  { value: 'WARNING', label: 'WARNING' },
  { value: 'CRITICAL', label: 'CRITICAL' },
] as const;

const GROUP_COLORS: Record<string, string> = {
  PRESSURE: '#d32f2f',
  TEMPERATURE: '#f57c00',
  RPM: '#ef6c00',
  SPEED: '#2e7d32',
  POSITION: '#1565c0',
  TIME: '#6d4c41',
  OTHER: '#616161',
};

const DEFAULT_ALGORITHM_OPTIONS = [
  { code: 'ISOLATION_FOREST', name: 'Isolation Forest' },
  { code: 'AUTOENCODER', name: 'Autoencoder' },
] as const;

const PRIORITY_EQUIPMENT_IDS = ['DEMO-MC-001', 'DEMO-MC-002', 'DEMO-MC-003'] as const;
const EQUIPMENT_NAME_FALLBACK: Record<string, string> = {
  'DEMO-MC-001': 'Demo Mixer 001',
  'DEMO-MC-002': 'Demo Press 002',
  'DEMO-MC-003': 'Demo Oven 003',
};

const RUN_LIST_LIMIT = 120;
const PER_EQUIPMENT_DATASET_PATTERN = /^demo_hmi_demo_mc_\d{3}_default_v\d+$/i;
const LEGACY_GLOBAL_DATASET_KEY = 'demo_hmi_all_default_v1';
const DEPRECATED_RUNTIME_DATASET_KEY = 'thisraw_all_default_v1';
const DEPRECATED_RUNTIME_DATASET_PREFIX = 'thisraw_';

type AnomalyDatasetOption = FeatureDataset & {
  normalizedDatasetKey: string;
  normalizedEquipmentId: string | null;
  isPerEquipmentDataset: boolean;
};

type EquipmentOption = {
  equipmentId: string;
  equipmentName: string;
};

function normalizeText(value: string | null | undefined): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function normalizeDatasetKey(value: string | null | undefined): string | null {
  return normalizeText(value);
}

function normalizeEquipmentId(value: string | null | undefined): string | null {
  const normalized = normalizeText(value);
  return normalized ? normalized.toUpperCase() : null;
}

function normalizeCompareKey(value: string | null | undefined): string {
  const normalized = normalizeDatasetKey(value);
  return normalized ? normalized.toLowerCase() : '';
}

function isPerEquipmentDataset(datasetKey: string): boolean {
  return PER_EQUIPMENT_DATASET_PATTERN.test(datasetKey.trim());
}

function isBlockedDatasetKey(datasetKey: string): boolean {
  const normalized = datasetKey.trim().toLowerCase();
  return normalized === LEGACY_GLOBAL_DATASET_KEY
    || normalized === DEPRECATED_RUNTIME_DATASET_KEY
    || normalized.startsWith(DEPRECATED_RUNTIME_DATASET_PREFIX);
}

function priorityEquipmentSort(left: string, right: string): number {
  const leftIndex = PRIORITY_EQUIPMENT_IDS.indexOf(left as (typeof PRIORITY_EQUIPMENT_IDS)[number]);
  const rightIndex = PRIORITY_EQUIPMENT_IDS.indexOf(right as (typeof PRIORITY_EQUIPMENT_IDS)[number]);
  const leftRank = leftIndex >= 0 ? leftIndex : Number.MAX_SAFE_INTEGER;
  const rightRank = rightIndex >= 0 ? rightIndex : Number.MAX_SAFE_INTEGER;
  if (leftRank !== rightRank) {
    return leftRank - rightRank;
  }
  return left.localeCompare(right);
}

function parseEquipmentIdFromDatasetKey(datasetKey: string | null | undefined): string | null {
  const normalized = normalizeDatasetKey(datasetKey)?.toLowerCase() ?? '';
  const matched = normalized.match(/_demo_mc_(\d{3})_/);
  if (!matched) {
    return null;
  }
  return `DEMO-MC-${matched[1]}`;
}

function resolveEquipmentName(equipmentId: string, datasetOptions: AnomalyDatasetOption[]): string {
  const fallback = EQUIPMENT_NAME_FALLBACK[equipmentId];
  if (fallback) {
    return fallback;
  }
  const scopedDataset = datasetOptions.find((dataset) => dataset.normalizedEquipmentId === equipmentId) ?? null;
  return normalizeText(scopedDataset?.dataset_name) ?? equipmentId;
}

function compareDatasetOption(left: AnomalyDatasetOption, right: AnomalyDatasetOption): number {
  if (left.isPerEquipmentDataset !== right.isPerEquipmentDataset) {
    return left.isPerEquipmentDataset ? -1 : 1;
  }
  const leftEquipmentId = left.normalizedEquipmentId ?? '';
  const rightEquipmentId = right.normalizedEquipmentId ?? '';
  const equipmentCompare = leftEquipmentId.localeCompare(rightEquipmentId);
  if (equipmentCompare !== 0) {
    return equipmentCompare;
  }
  return left.dataset_key.localeCompare(right.dataset_key);
}

function toAnomalyDatasetOptions(featureDatasets: FeatureDataset[]): AnomalyDatasetOption[] {
  const mapped = featureDatasets
    .map((dataset) => {
      const normalizedDatasetKey = normalizeDatasetKey(dataset.dataset_key);
      if (!normalizedDatasetKey) {
        return null;
      }
      if (isBlockedDatasetKey(normalizedDatasetKey)) {
        return null;
      }
      return {
        ...dataset,
        normalizedDatasetKey,
        normalizedEquipmentId: normalizeEquipmentId(dataset.equipment_id),
        isPerEquipmentDataset: isPerEquipmentDataset(normalizedDatasetKey),
      } satisfies AnomalyDatasetOption;
    })
    .filter((dataset): dataset is AnomalyDatasetOption => dataset !== null);

  mapped.sort(compareDatasetOption);
  return mapped;
}

function toOperationDatasetOption(option: AnomalyDatasetOption, sortNo: number): OperationDatasetOption {
  return {
    datasetKey: option.dataset_key,
    datasetName: option.dataset_name,
    displayName: option.dataset_label,
    typeCode: null,
    dtlCode: null,
    sourceCollection: option.source_collection,
    datasetPurpose: 'FEATURE_SOURCE',
    featureEnabled: true,
    sortNo,
  };
}

function resolvePreferredDatasetForEquipment(
  equipmentId: string,
  datasetOptions: AnomalyDatasetOption[],
  currentDatasetKey?: string | null,
): AnomalyDatasetOption | null {
  const scopedDatasets = datasetOptions.filter((dataset) => dataset.normalizedEquipmentId === equipmentId);
  if (scopedDatasets.length === 0) {
    return null;
  }
  const normalizedCurrent = normalizeCompareKey(currentDatasetKey);
  if (normalizedCurrent) {
    const matchedCurrent = scopedDatasets.find(
      (dataset) => dataset.normalizedDatasetKey.toLowerCase() === normalizedCurrent,
    );
    if (matchedCurrent) {
      return matchedCurrent;
    }
  }
  const expectedDatasetKey = `demo_hmi_${equipmentId.toLowerCase()}_default_v1`;
  const matchedExpected = scopedDatasets.find(
    (dataset) => dataset.normalizedDatasetKey.toLowerCase() === expectedDatasetKey,
  );
  if (matchedExpected) {
    return matchedExpected;
  }
  return scopedDatasets[0];
}

function toAnomalyCauseRun(run: AnomalyRunOption): AnomalyCauseRun {
  const datasetKey = normalizeDatasetKey(run.dataset_key);
  const equipmentId = normalizeEquipmentId(run.equipment_id) ?? parseEquipmentIdFromDatasetKey(datasetKey);
  const executedAt = normalizeText(run.updated_at) ?? normalizeText(run.reg_date);
  const status = normalizeText(run.status)?.toUpperCase() ?? null;
  const algoCode = normalizeText(run.algo_code)?.toUpperCase() ?? null;
  const algoName = normalizeText(run.algo_name) ?? algoCode;
  const label = [
    run.run_id,
    formatDateTime(executedAt),
    status ?? '-',
    datasetKey ?? '-',
    equipmentId ?? '-',
  ].join(' | ');

  return {
    runId: run.run_id,
    datasetKey,
    equipmentId,
    algoCode,
    algoName,
    status,
    label,
    executedAt,
  };
}

function compareRun(left: AnomalyCauseRun, right: AnomalyCauseRun): number {
  const leftSuccessRank = (left.status ?? '').toUpperCase() === 'SUCCESS' ? 0 : 1;
  const rightSuccessRank = (right.status ?? '').toUpperCase() === 'SUCCESS' ? 0 : 1;
  if (leftSuccessRank !== rightSuccessRank) {
    return leftSuccessRank - rightSuccessRank;
  }

  const leftTime = Date.parse(left.executedAt ?? '');
  const rightTime = Date.parse(right.executedAt ?? '');
  const normalizedLeftTime = Number.isNaN(leftTime) ? 0 : leftTime;
  const normalizedRightTime = Number.isNaN(rightTime) ? 0 : rightTime;
  if (normalizedLeftTime !== normalizedRightTime) {
    return normalizedRightTime - normalizedLeftTime;
  }
  return right.runId.localeCompare(left.runId);
}

function formatDateTime(value: string | null | undefined): string {
  const text = value?.trim();
  if (!text) {
    return '-';
  }
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) {
    return text;
  }
  return date.toLocaleString('ko-KR', { hour12: false });
}

function formatDecimal(value: number | null | undefined, digits = 2): string {
  if (value == null || Number.isNaN(value)) {
    return '-';
  }
  return value.toFixed(digits);
}

function formatAnomalyScore(value: number | null | undefined): string {
  return formatDecimal(value, 4);
}

function formatHealthIndex(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '-';
  }
  return `${value.toFixed(4)} (${(value * 100).toFixed(1)}%)`;
}

function toStatusStyle(status: string | null | undefined): { label: string; color: string; backgroundColor: string } {
  const normalized = status?.trim().toUpperCase();
  if (normalized === 'NORMAL') {
    return { label: 'NORMAL', color: '#1b5e20', backgroundColor: '#e8f5e9' };
  }
  if (normalized === 'WARNING') {
    return { label: 'WARNING', color: '#a84300', backgroundColor: '#fff3e0' };
  }
  if (normalized === 'CRITICAL') {
    return { label: 'CRITICAL', color: '#b71c1c', backgroundColor: '#ffebee' };
  }
  return { label: normalized ?? '-', color: '#455a64', backgroundColor: '#eceff1' };
}

function toDirectionLabel(direction: string | null | undefined): string {
  const normalized = direction?.trim().toUpperCase();
  if (normalized === 'HIGH') {
    return '상승';
  }
  if (normalized === 'LOW') {
    return '하락';
  }
  if (normalized === 'FLAT') {
    return '변화없음';
  }
  return '판단 불가';
}

function toDirectionColor(direction: string | null | undefined): string {
  const normalized = direction?.trim().toUpperCase();
  if (normalized === 'HIGH') {
    return '#c62828';
  }
  if (normalized === 'LOW') {
    return '#1565c0';
  }
  return '#616161';
}

function toUtcIso(datetimeLocal: string): string | null {
  const text = datetimeLocal.trim();
  if (!text) {
    return null;
  }
  const parsed = new Date(text);
  if (Number.isNaN(parsed.getTime())) {
    return text;
  }
  return parsed.toISOString();
}

function badge(text: string, color: string, backgroundColor: string, key?: string) {
  return (
    <Box
      key={key ?? text}
      component="span"
      sx={{
        px: 1,
        py: 0.35,
        borderRadius: 999,
        fontSize: 12,
        fontWeight: 700,
        color,
        backgroundColor,
        border: '1px solid rgba(0,0,0,0.06)',
        whiteSpace: 'nowrap',
      }}
    >
      {text}
    </Box>
  );
}

function sortedGroupScores(scores: GroupScore[]): GroupScore[] {
  return [...scores].sort((left, right) => {
    const leftRank = left.rank ?? Number.MAX_SAFE_INTEGER;
    const rightRank = right.rank ?? Number.MAX_SAFE_INTEGER;
    if (leftRank !== rightRank) {
      return leftRank - rightRank;
    }
    const leftScore = left.score ?? Number.NEGATIVE_INFINITY;
    const rightScore = right.score ?? Number.NEGATIVE_INFINITY;
    return rightScore - leftScore;
  });
}

function sortedCauseCandidates(candidates: CauseCandidate[]): CauseCandidate[] {
  return [...candidates].sort((left, right) => {
    const leftRank = left.rank ?? Number.MAX_SAFE_INTEGER;
    const rightRank = right.rank ?? Number.MAX_SAFE_INTEGER;
    if (leftRank !== rightRank) {
      return leftRank - rightRank;
    }
    const leftScore = left.deviationScore ?? Number.NEGATIVE_INFINITY;
    const rightScore = right.deviationScore ?? Number.NEGATIVE_INFINITY;
    return rightScore - leftScore;
  });
}

function normalizeAlgoCode(value: string | null | undefined): string {
  return value?.trim().toUpperCase() ?? '';
}

export function AnomalyCausePage() {
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();

  const stateDatasetKey = useMemo(() => extractDatasetKeyFromRouteState(location.state), [location.state]);
  const queryDatasetKey = useMemo(() => normalizeText(searchParams.get('datasetKey')) ?? '', [searchParams]);
  const queryRunId = useMemo(() => normalizeText(searchParams.get('runId')) ?? '', [searchParams]);

  const [runs, setRuns] = useState<AnomalyCauseRun[]>([]);
  const [algorithmOptions, setAlgorithmOptions] = useState<{ code: string; name: string }[]>(
    DEFAULT_ALGORITHM_OPTIONS.map((option) => ({ ...option })),
  );
  const [datasetOptions, setDatasetOptions] = useState<AnomalyDatasetOption[]>([]);
  const [selectedEquipmentId, setSelectedEquipmentId] = useState<string>('');
  const [selectedDatasetKey, setSelectedDatasetKey] = useState<string>('');
  const [datasetSelectionWarning, setDatasetSelectionWarning] = useState<string | null>(null);
  const [runSelectionNotice, setRunSelectionNotice] = useState<string | null>(null);
  const [loadingDatasets, setLoadingDatasets] = useState(false);
  const [loadingRuns, setLoadingRuns] = useState(false);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);

  const [selectedAlgoCode, setSelectedAlgoCode] = useState<string>(DEFAULT_ALGORITHM_OPTIONS[0].code);
  const [selectedRunId, setSelectedRunId] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [fromDateTime, setFromDateTime] = useState<string>('');
  const [toDateTime, setToDateTime] = useState<string>('');

  const [listResponse, setListResponse] = useState<AnomalyCauseListResponse>(DEFAULT_LIST_RESPONSE);
  const [selectedWindow, setSelectedWindow] = useState<AnomalyCauseListItem | null>(null);
  const [detail, setDetail] = useState<AnomalyCauseDetail | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [lastFetchedAt, setLastFetchedAt] = useState<string | null>(null);
  const [recalculatingRun, setRecalculatingRun] = useState(false);
  const [actionNotice, setActionNotice] = useState<{ severity: 'success' | 'error'; message: string } | null>(null);

  const filteredRuns = useMemo(() => {
    return runs.filter((run) => normalizeAlgoCode(run.algoCode) === selectedAlgoCode);
  }, [runs, selectedAlgoCode]);

  const equipmentOptions = useMemo(() => {
    const distinctEquipmentIds = Array.from(
      new Set(
        datasetOptions
          .map((option) => option.normalizedEquipmentId)
          .filter((equipmentId): equipmentId is string => equipmentId !== null),
      ),
    );
    distinctEquipmentIds.sort(priorityEquipmentSort);
    return distinctEquipmentIds.map((equipmentId) => ({
      equipmentId,
      equipmentName: resolveEquipmentName(equipmentId, datasetOptions),
    })) satisfies EquipmentOption[];
  }, [datasetOptions]);

  const selectedRun = useMemo(
    () => filteredRuns.find((run) => run.runId === selectedRunId) ?? null,
    [filteredRuns, selectedRunId],
  );

  const selectedEquipmentDisplayText = useMemo(() => {
    const normalizedEquipmentId = normalizeEquipmentId(selectedEquipmentId);
    if (!normalizedEquipmentId) {
      return '-';
    }
    const option = equipmentOptions.find((item) => item.equipmentId === normalizedEquipmentId) ?? null;
    const equipmentName = option?.equipmentName ?? resolveEquipmentName(normalizedEquipmentId, datasetOptions);
    return `${normalizedEquipmentId} / ${equipmentName}`;
  }, [datasetOptions, equipmentOptions, selectedEquipmentId]);

  const selectedEquipmentTitlePrefix = useMemo(
    () => normalizeEquipmentId(selectedEquipmentId) ?? '설비 미선택',
    [selectedEquipmentId],
  );

  const selectedRunStatusText = useMemo(() => {
    if (!selectedRun) {
      return '-';
    }
    return normalizeText(selectedRun.status) ?? '-';
  }, [selectedRun]);

  const candidateRows = useMemo(() => {
    if (!detail) {
      return [];
    }
    return sortedCauseCandidates(detail.causeCandidates);
  }, [detail]);

  const top3Candidates = useMemo(() => candidateRows.slice(0, 3), [candidateRows]);

  const groupScoreRows = useMemo(() => {
    if (!detail) {
      return [];
    }
    return sortedGroupScores(detail.groupScores);
  }, [detail]);

  const maxGroupScore = useMemo(() => {
    const scores = groupScoreRows.map((row) => row.score ?? 0);
    if (scores.length === 0) {
      return 1;
    }
    return Math.max(1, ...scores);
  }, [groupScoreRows]);

  const loadDatasetOptions = useCallback(async () => {
    setLoadingDatasets(true);
    setDatasetSelectionWarning(null);
    setPageError(null);

    try {
      const response = await modelTrainService.getFeatureDatasets();
      const normalizedOptions = toAnomalyDatasetOptions(response.feature_datasets ?? []);
      setDatasetOptions(normalizedOptions);

      const operationalOptions = normalizedOptions.map((option, index) =>
        toOperationDatasetOption(option, index + 1),
      );
      const perEquipmentOptions = normalizedOptions
        .filter((option) => option.isPerEquipmentDataset)
        .map((option, index) => toOperationDatasetOption(option, index + 1));
      const resolverOptions = perEquipmentOptions.length > 0 ? perEquipmentOptions : operationalOptions;
      const resolved = resolveOperationDatasetKeyWithFallback({
        datasetOptions: resolverOptions,
        queryDatasetKey,
        stateDatasetKey,
        persistedDatasetKey: readPersistedOperationDatasetKey(),
      });

      const resolvedDatasetOption = normalizedOptions.find(
        (option) => option.normalizedDatasetKey.toLowerCase() === normalizeCompareKey(resolved.datasetKey),
      ) ?? null;
      const allEquipmentIds = Array.from(
        new Set(
          normalizedOptions
            .map((option) => option.normalizedEquipmentId)
            .filter((equipmentId): equipmentId is string => equipmentId !== null),
        ),
      ).sort(priorityEquipmentSort);
      const resolvedEquipmentId = resolvedDatasetOption?.normalizedEquipmentId
        ?? parseEquipmentIdFromDatasetKey(resolved.datasetKey)
        ?? allEquipmentIds[0]
        ?? '';
      const preferredDataset = resolvedEquipmentId
        ? resolvePreferredDatasetForEquipment(resolvedEquipmentId, normalizedOptions, resolved.datasetKey)
        : null;
      const fallbackDataset = preferredDataset ?? normalizedOptions[0] ?? null;

      setSelectedEquipmentId(resolvedEquipmentId);
      setSelectedDatasetKey(fallbackDataset?.dataset_key ?? resolved.datasetKey);
      setDatasetSelectionWarning(resolved.warning);
      setLastFetchedAt(new Date().toISOString());
    } catch (error: unknown) {
      setDatasetOptions([]);
      setSelectedEquipmentId('');
      setSelectedDatasetKey('');
      setDatasetSelectionWarning('dataset 목록을 불러오지 못했습니다.');
      setPageError(error instanceof Error ? error.message : 'dataset 목록을 불러오지 못했습니다.');
    } finally {
      setLoadingDatasets(false);
    }
  }, [queryDatasetKey, stateDatasetKey]);

  const loadRuns = useCallback(async () => {
    const normalizedDatasetKey = normalizeDatasetKey(selectedDatasetKey);
    const normalizedEquipmentId = normalizeEquipmentId(selectedEquipmentId);
    if (!normalizedDatasetKey || !normalizedEquipmentId) {
      setRuns([]);
      setSelectedRunId('');
      return;
    }

    setLoadingRuns(true);
    setRunSelectionNotice(null);
    setPageError(null);

    try {
      const response = await modelTrainService.getAnomalyRunOptions({
        algoCode: selectedAlgoCode,
        datasetKey: normalizedDatasetKey,
        equipmentId: normalizedEquipmentId,
        includeNonSuccess: true,
        limit: RUN_LIST_LIMIT,
      });

      const nextAlgorithmOptions = response.algorithms
        .map((algorithm) => ({
          code: normalizeAlgoCode(algorithm.algo_code),
          name: normalizeText(algorithm.algo_name) ?? normalizeAlgoCode(algorithm.algo_code),
        }))
        .filter((algorithm) => Boolean(algorithm.code));
      setAlgorithmOptions(
        nextAlgorithmOptions.length > 0
          ? nextAlgorithmOptions
          : DEFAULT_ALGORITHM_OPTIONS.map((option) => ({ ...option })),
      );

      const scopedRuns = (response.runs ?? [])
        .map(toAnomalyCauseRun)
        .filter(
          (run) =>
            normalizeCompareKey(run.datasetKey) === normalizedDatasetKey.toLowerCase()
            && normalizeEquipmentId(run.equipmentId) === normalizedEquipmentId,
        )
        .sort(compareRun);
      setRuns(scopedRuns);

      const runIds = new Set(scopedRuns.map((run) => run.runId));
      const normalizedQueryRunId = normalizeText(queryRunId);
      const normalizedCurrentRunId = normalizeText(selectedRunId);
      let nextSelectedRunId = '';
      if (normalizedQueryRunId && runIds.has(normalizedQueryRunId)) {
        nextSelectedRunId = normalizedQueryRunId;
      } else if (normalizedCurrentRunId && runIds.has(normalizedCurrentRunId)) {
        nextSelectedRunId = normalizedCurrentRunId;
      } else if (response.latest_success_run_id && runIds.has(response.latest_success_run_id)) {
        nextSelectedRunId = response.latest_success_run_id;
      } else if (scopedRuns.length > 0) {
        nextSelectedRunId = scopedRuns[0].runId;
      }
      setSelectedRunId(nextSelectedRunId);

      if (normalizedQueryRunId && !runIds.has(normalizedQueryRunId)) {
        if (nextSelectedRunId) {
          setRunSelectionNotice(`잘못된 runId(${normalizedQueryRunId}) 요청으로 ${nextSelectedRunId}로 전환했습니다.`);
        } else {
          setRunSelectionNotice(`잘못된 runId(${normalizedQueryRunId}) 요청으로 실행 데이터를 찾지 못했습니다.`);
        }
      } else if (scopedRuns.length === 0) {
        setRunSelectionNotice('선택한 설비/알고리즘 조건에 해당하는 실행 데이터가 없습니다.');
      } else if (!response.latest_success_run_id) {
        setRunSelectionNotice('SUCCESS 상태 실행 데이터가 없어 최신 실행 데이터로 선택했습니다.');
      }
      setLastFetchedAt(new Date().toISOString());
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : LOAD_ERROR_MESSAGE;
      setPageError(message);
      setRuns([]);
      setSelectedRunId('');
      setListResponse(DEFAULT_LIST_RESPONSE);
      setSelectedWindow(null);
      setDetail(null);
    } finally {
      setLoadingRuns(false);
    }
  }, [queryRunId, selectedAlgoCode, selectedDatasetKey, selectedEquipmentId, selectedRunId]);

  const loadDetail = useCallback(async (windowItem: AnomalyCauseListItem) => {
    if (!windowItem.causeGenerated) {
      setDetail(null);
      setLoadingDetail(false);
      return;
    }

    const normalizedEquipmentId = normalizeEquipmentId(windowItem.equipmentId)
      ?? normalizeEquipmentId(selectedEquipmentId);
    if (!normalizedEquipmentId) {
      setPageError('equipmentId is required.');
      setDetail(null);
      setLoadingDetail(false);
      return;
    }

    setLoadingDetail(true);
    try {
      const rawWindowStart = windowItem.windowStart ?? '';
      const rawWindowEnd = windowItem.windowEnd ?? '';
      const normalizedWindowStart = rawWindowStart <= rawWindowEnd ? rawWindowStart : rawWindowEnd;
      const normalizedWindowEnd = rawWindowStart <= rawWindowEnd ? rawWindowEnd : rawWindowStart;

      const response = await anomalyCauseService.fetchAnomalyCauseDetail({
        runId: windowItem.runId,
        datasetKey: windowItem.datasetKey,
        equipmentId: normalizedEquipmentId,
        windowStart: normalizedWindowStart,
        windowEnd: normalizedWindowEnd,
      });

      const responseEquipmentId = normalizeEquipmentId(response.equipmentId);
      if (responseEquipmentId && responseEquipmentId !== normalizedEquipmentId) {
        throw new Error('Selected window detail equipment_id does not match current selection.');
      }
      setDetail(response);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : LOAD_ERROR_MESSAGE;
      setPageError(message);
      setDetail(null);
    } finally {
      setLoadingDetail(false);
    }
  }, [selectedEquipmentId]);

  const loadList = useCallback(
    async (targetPage: number) => {
      const normalizedDatasetKey = normalizeDatasetKey(selectedDatasetKey);
      const normalizedEquipmentId = normalizeEquipmentId(selectedEquipmentId);
      if (!selectedRun || !normalizedDatasetKey || !normalizedEquipmentId) {
        setListResponse(DEFAULT_LIST_RESPONSE);
        setSelectedWindow(null);
        setDetail(null);
        return;
      }

      const runDatasetKey = normalizeDatasetKey(selectedRun.datasetKey);
      const runEquipmentId = normalizeEquipmentId(selectedRun.equipmentId);
      if (runDatasetKey && runDatasetKey.toLowerCase() !== normalizedDatasetKey.toLowerCase()) {
        setPageError('Selected run does not match current dataset selection.');
        setListResponse(DEFAULT_LIST_RESPONSE);
        setSelectedWindow(null);
        setDetail(null);
        return;
      }
      if (runEquipmentId && runEquipmentId !== normalizedEquipmentId) {
        setPageError('Selected run does not match current equipment selection.');
        setListResponse(DEFAULT_LIST_RESPONSE);
        setSelectedWindow(null);
        setDetail(null);
        return;
      }

      setLoadingList(true);
      setLoadingDetail(false);
      setPageError(null);

      try {
        const response = await anomalyCauseService.fetchAnomalyCauseList({
          runId: selectedRun.runId,
          datasetKey: normalizedDatasetKey,
          equipmentId: normalizedEquipmentId,
          status: statusFilter === 'ALL' ? null : statusFilter,
          from: toUtcIso(fromDateTime),
          to: toUtcIso(toDateTime),
          page: targetPage,
          size: listResponse.size || DEFAULT_PAGE_SIZE,
        });

        const distinctEquipmentIds = new Set(
          response.items
            .map((item) => normalizeEquipmentId(item.equipmentId))
            .filter((equipmentId): equipmentId is string => equipmentId !== null),
        );
        if (distinctEquipmentIds.size > 1) {
          throw new Error('Multiple equipment_id values found in window list.');
        }
        if (distinctEquipmentIds.size === 1 && !distinctEquipmentIds.has(normalizedEquipmentId)) {
          throw new Error('Window list equipment_id does not match current selection.');
        }

        setListResponse(response);
        setLastFetchedAt(new Date().toISOString());

        if (response.items.length === 0) {
          setSelectedWindow(null);
          setDetail(null);
          return;
        }

        const nextSelected = response.items[0];
        setSelectedWindow(nextSelected);
        await loadDetail(nextSelected);
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : LOAD_ERROR_MESSAGE;
        setPageError(message);
        setListResponse({
          ...DEFAULT_LIST_RESPONSE,
          size: listResponse.size || DEFAULT_PAGE_SIZE,
        });
        setSelectedWindow(null);
        setDetail(null);
      } finally {
        setLoadingList(false);
      }
    },
    [selectedRun, selectedDatasetKey, selectedEquipmentId, statusFilter, fromDateTime, toDateTime, listResponse.size, loadDetail],
  );

  useEffect(() => {
    void loadDatasetOptions();
  }, [loadDatasetOptions]);

  useEffect(() => {
    if (algorithmOptions.length === 0) {
      setSelectedAlgoCode(DEFAULT_ALGORITHM_OPTIONS[0].code);
      return;
    }
    const exists = algorithmOptions.some((item) => item.code === selectedAlgoCode);
    if (!exists) {
      setSelectedAlgoCode(algorithmOptions[0].code);
    }
  }, [algorithmOptions, selectedAlgoCode]);

  useEffect(() => {
    const normalizedDatasetKey = normalizeDatasetKey(selectedDatasetKey);
    const normalizedEquipmentId = normalizeEquipmentId(selectedEquipmentId);
    if (!normalizedDatasetKey || !normalizedEquipmentId) {
      setRuns([]);
      setSelectedRunId('');
      setRunSelectionNotice(null);
      setListResponse(DEFAULT_LIST_RESPONSE);
      setSelectedWindow(null);
      setDetail(null);
      return;
    }
    void loadRuns();
  }, [loadRuns, selectedDatasetKey, selectedEquipmentId, selectedAlgoCode]);

  useEffect(() => {
    const normalizedDatasetKey = normalizeDatasetKey(selectedDatasetKey);
    if (!normalizedDatasetKey) {
      const nextParams = new URLSearchParams(searchParams);
      let hasChanged = false;
      if (nextParams.has('datasetKey')) {
        nextParams.delete('datasetKey');
        hasChanged = true;
      }
      if (nextParams.has('runId')) {
        nextParams.delete('runId');
        hasChanged = true;
      }
      if (hasChanged) {
        setSearchParams(nextParams, { replace: true });
      }
      return;
    }

    persistOperationDatasetKey(normalizedDatasetKey);

    const normalizedRunId = normalizeText(selectedRunId);
    const nextParams = new URLSearchParams(searchParams);
    let hasChanged = false;
    if (nextParams.get('datasetKey') !== normalizedDatasetKey) {
      nextParams.set('datasetKey', normalizedDatasetKey);
      hasChanged = true;
    }
    if (normalizedRunId) {
      if (nextParams.get('runId') !== normalizedRunId) {
        nextParams.set('runId', normalizedRunId);
        hasChanged = true;
      }
    } else if (nextParams.has('runId')) {
      nextParams.delete('runId');
      hasChanged = true;
    }
    if (hasChanged) {
      setSearchParams(nextParams, { replace: true });
    }
  }, [searchParams, selectedDatasetKey, selectedRunId, setSearchParams]);

  useEffect(() => {
    if (!selectedRun || !selectedDatasetKey || !selectedEquipmentId) {
      setListResponse(DEFAULT_LIST_RESPONSE);
      setSelectedWindow(null);
      setDetail(null);
      return;
    }
    void loadList(0);
  }, [selectedRun, selectedDatasetKey, selectedEquipmentId, loadList]);

  const handleSearch = async () => {
    await loadList(0);
  };

  const handleRefresh = async () => {
    await loadDatasetOptions();
  };

  const handleRecalculateRun = async () => {
    const normalizedDatasetKey = normalizeDatasetKey(selectedDatasetKey);
    const normalizedEquipmentId = normalizeEquipmentId(selectedEquipmentId);
    if (!selectedRun || !normalizedDatasetKey || !normalizedEquipmentId || recalculatingRun) {
      return;
    }

    setActionNotice(null);
    setRecalculatingRun(true);
    try {
      await anomalyCauseService.recalculateAnomalyCauseRun({
        runId: selectedRun.runId,
        datasetKey: normalizedDatasetKey,
        equipmentId: normalizedEquipmentId,
      });
      setActionNotice({ severity: 'success', message: '원인 후보 재계산이 완료되었습니다.' });
      await loadList(0);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : LOAD_ERROR_MESSAGE;
      setActionNotice({ severity: 'error', message });
    } finally {
      setRecalculatingRun(false);
    }
  };

  const handleSelectWindow = async (windowItem: AnomalyCauseListItem) => {
    setSelectedWindow(windowItem);
    await loadDetail(windowItem);
  };

  const handleEquipmentChange = useCallback(
    (equipmentId: string) => {
      const normalizedEquipmentId = normalizeEquipmentId(equipmentId);
      setRuns([]);
      setSelectedRunId('');
      setRunSelectionNotice(null);
      setListResponse(DEFAULT_LIST_RESPONSE);
      setSelectedWindow(null);
      setDetail(null);

      if (!normalizedEquipmentId) {
        setSelectedEquipmentId('');
        setSelectedDatasetKey('');
        return;
      }

      const preferredDataset = resolvePreferredDatasetForEquipment(
        normalizedEquipmentId,
        datasetOptions,
        selectedDatasetKey,
      );
      setSelectedEquipmentId(normalizedEquipmentId);
      setSelectedDatasetKey(preferredDataset?.dataset_key ?? '');
    },
    [datasetOptions, selectedDatasetKey],
  );

  const currentStatusStyle = toStatusStyle(detail?.anomalyStatus ?? selectedWindow?.status);
  const summaryGroups = (detail?.causeSummary ?? selectedWindow?.causeSummary ?? []).filter(
    (group): group is string => Boolean(group && group.trim()),
  );
  return (
    <Stack spacing={1.6} sx={{ pb: 2 }}>
      <Card variant="outlined" sx={{ borderColor: '#d6e0ef' }}>
        <CardContent sx={{ py: 2.2 }}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            spacing={1.5}
          >
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.6 }}>
                AI 원인 분석
              </Typography>
              <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
                이상탐지 결과를 기준으로 원인 후보를 분석하고 확인합니다.
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.2 }}>
                확정 원인이 아닌 통계 기반 편차/이상값이 큰 원인 후보입니다.
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.7, display: 'block' }}>
                마지막 조회 시점: {formatDateTime(lastFetchedAt)}
              </Typography>
            </Box>

            <Button
              variant="outlined"
              startIcon={<RefreshRoundedIcon />}
              onClick={() => void handleRefresh()}
              disabled={loadingDatasets || loadingRuns}
              sx={{ minWidth: 130, fontWeight: 700 }}
            >
              {loadingDatasets || loadingRuns ? '새로고침 중..' : '새로고침'}
            </Button>
          </Stack>
        </CardContent>
      </Card>

      {pageError ? (
        <Alert severity="error">
          {LOAD_ERROR_MESSAGE}
          <Typography variant="caption" component="div" sx={{ mt: 0.5 }}>
            {pageError}
          </Typography>
        </Alert>
      ) : null}

      {actionNotice ? <Alert severity={actionNotice.severity}>{actionNotice.message}</Alert> : null}
      {datasetSelectionWarning ? <Alert severity="warning">{datasetSelectionWarning}</Alert> : null}
      {runSelectionNotice ? <Alert severity="warning">{runSelectionNotice}</Alert> : null}

      <Card variant="outlined">
        <CardContent sx={{ py: 1.8 }}>
          <Typography variant="subtitle1" fontWeight={800}>
            조회 조건
          </Typography>
          <Divider sx={{ my: 1 }} />

          <Box
            sx={{
              display: 'grid',
              gap: 1,
              gridTemplateColumns: { xs: '1fr', lg: '170px 190px minmax(260px, 1fr) 190px 190px 160px 112px' },
              alignItems: 'center',
            }}
          >
            <TextField
              select
              size="small"
              label="알고리즘 선택"
              value={selectedAlgoCode}
              onChange={(event) => {
                setSelectedAlgoCode(event.target.value);
                setSelectedRunId('');
              }}
            >
              {algorithmOptions.map((algorithm) => (
                <MenuItem key={algorithm.code} value={algorithm.code}>
                  {algorithm.name}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              size="small"
              label="설비 선택"
              value={normalizeEquipmentId(selectedEquipmentId) ?? ''}
              onChange={(event) => handleEquipmentChange(event.target.value)}
              disabled={loadingDatasets || equipmentOptions.length === 0}
            >
              {equipmentOptions.map((equipmentOption) => (
                <MenuItem key={equipmentOption.equipmentId} value={equipmentOption.equipmentId}>
                  {equipmentOption.equipmentId} / {equipmentOption.equipmentName}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              size="small"
              label="실행 데이터 선택"
              value={selectedRunId}
              onChange={(event) => setSelectedRunId(event.target.value)}
              disabled={loadingRuns || !selectedEquipmentId || filteredRuns.length === 0}
            >
              {filteredRuns.map((runOption) => (
                <MenuItem key={runOption.runId} value={runOption.runId}>
                  {runOption.label}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              size="small"
              label="기간 시작"
              type="datetime-local"
              value={fromDateTime}
              onChange={(event) => setFromDateTime(event.target.value)}
              InputLabelProps={{ shrink: true }}
            />

            <TextField
              size="small"
              label="기간 종료"
              type="datetime-local"
              value={toDateTime}
              onChange={(event) => setToDateTime(event.target.value)}
              InputLabelProps={{ shrink: true }}
            />

            <TextField
              select
              size="small"
              label="상태 선택"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              {STATUS_OPTIONS.map((statusOption) => (
                <MenuItem key={statusOption.value} value={statusOption.value}>
                  {statusOption.label}
                </MenuItem>
              ))}
            </TextField>

            <Button
              variant="contained"
              startIcon={<SearchRoundedIcon />}
              disabled={!selectedRun || !selectedDatasetKey || !selectedEquipmentId || loadingList}
              onClick={() => void handleSearch()}
              sx={{ minHeight: 40, fontWeight: 800 }}
            >
              조회
            </Button>
          </Box>

          {loadingDatasets ? (
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
              <CircularProgress size={18} />
              <Typography variant="body2" color="text.secondary">
                설비/데이터셋 목록을 불러오는 중입니다.
              </Typography>
            </Stack>
          ) : null}

          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            spacing={1}
            sx={{ mt: 1.1 }}
          >
            <Typography variant="body2" color="text.secondary">
              선택 설비: {selectedEquipmentDisplayText} / 데이터셋: {selectedDatasetKey || '-'} / 실행:{' '}
              {selectedRun?.runId ?? '-'} / 상태: {selectedRunStatusText} / 기준: thisanomalycause 저장 결과
            </Typography>
            <Stack spacing={0.35} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
              <Button
                variant="outlined"
                color="secondary"
                disabled={!selectedRun || !selectedDatasetKey || !selectedEquipmentId || loadingList || recalculatingRun}
                onClick={() => void handleRecalculateRun()}
                sx={{ minWidth: 210, fontWeight: 700 }}
              >
                {recalculatingRun
                  ? '원인 후보 재계산 중..'
                  : selectedRun
                    ? '선택 실행 원인 후보 재계산'
                    : '원인 후보 재계산'}
              </Button>
              <Typography variant="caption" color="text.secondary">
                원인 후보는 모델 실행 완료 후 자동 생성됩니다.
              </Typography>
              <Typography variant="caption" color="text.secondary">
                이 버튼은 데이터 보정 또는 재계산이 필요한 경우에만 사용합니다.
              </Typography>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {!loadingRuns && runs.length === 0 ? (
        <Alert severity="info">
          {selectedEquipmentId
            ? `선택한 설비(${normalizeEquipmentId(selectedEquipmentId)})/알고리즘(${selectedAlgoCode}) 조건에 해당하는 실행 데이터가 없습니다.`
            : '설비를 먼저 선택해 주세요.'}
        </Alert>
      ) : null}

      <Box
        sx={{
          display: 'grid',
          gap: 1.6,
          gridTemplateColumns: { xs: '1fr', xl: '1.18fr 1fr' },
          alignItems: 'stretch',
        }}
      >
        <Card variant="outlined" sx={{ minHeight: 0 }}>
          <CardContent sx={{ py: 1.7 }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                {selectedEquipmentTitlePrefix} 이상 탐지 Window 목록
              </Typography>
              <Typography variant="body2" color="text.secondary">
                총 {listResponse.total.toLocaleString('ko-KR')}건
              </Typography>
            </Stack>
            <Divider sx={{ my: 1 }} />

            {loadingList ? (
              <Stack direction="row" spacing={1} alignItems="center" sx={{ py: 2 }}>
                <CircularProgress size={20} />
                <Typography variant="body2" color="text.secondary">
                  조회 데이터를 불러오는 중입니다.
                </Typography>
              </Stack>
            ) : null}

            {!loadingList && listResponse.items.length === 0 ? (
              <Alert severity="info">조회 조건에 해당하는 이상탐지 window가 없습니다.</Alert>
            ) : null}

            {listResponse.items.length > 0 ? (
              <>
                <TableContainer sx={{ border: '1px solid #d7e0ee', borderRadius: 1, height: 390 }}>
                  <Table stickyHeader size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell sx={{ fontWeight: 800, width: 54 }}>선택</TableCell>
                        <TableCell sx={{ fontWeight: 800, minWidth: 210 }}>시간 (Window)</TableCell>
                        <TableCell sx={{ fontWeight: 800, width: 94 }}>상태</TableCell>
                        <TableCell sx={{ fontWeight: 800, width: 96 }}>이상 점수</TableCell>
                        <TableCell sx={{ fontWeight: 800, minWidth: 170 }}>주요 상위 후보</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {listResponse.items.map((windowItem) => {
                        const isSelected = selectedWindow?.id === windowItem.id;
                        const statusStyle = toStatusStyle(windowItem.status);
                        const summary = windowItem.causeSummary.slice(0, 3);

                        return (
                          <TableRow
                            key={windowItem.id}
                            hover
                            selected={isSelected}
                            onClick={() => void handleSelectWindow(windowItem)}
                            sx={{ cursor: 'pointer' }}
                          >
                            <TableCell>
                              <Box
                                sx={{
                                  width: 13,
                                  height: 13,
                                  borderRadius: '50%',
                                  border: '2px solid',
                                  borderColor: isSelected ? '#1565c0' : '#b0bec5',
                                  backgroundColor: isSelected ? '#1565c0' : 'transparent',
                                }}
                              />
                            </TableCell>
                            <TableCell>
                              <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                                {formatDateTime(windowItem.windowStart)}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                ~ {formatDateTime(windowItem.windowEnd)}
                              </Typography>
                            </TableCell>
                            <TableCell>
                              {badge(statusStyle.label, statusStyle.color, statusStyle.backgroundColor)}
                            </TableCell>
                            <TableCell>{formatAnomalyScore(windowItem.anomalyScore)}</TableCell>
                            <TableCell>
                              {windowItem.causeGenerated ? (
                                <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
                                  {summary.length > 0
                                    ? summary.map((summaryLabel) =>
                                        badge(summaryLabel, '#0d47a1', '#e3f2fd', summaryLabel),
                                      )
                                    : badge('원인 후보 없음', '#455a64', '#eceff1')}
                                </Stack>
                              ) : (
                                badge('원인 후보 미생성', '#455a64', '#eceff1')
                              )}
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>

                <TablePagination
                  component="div"
                  rowsPerPageOptions={[listResponse.size]}
                  rowsPerPage={listResponse.size}
                  count={listResponse.total}
                  page={listResponse.page}
                  onPageChange={(_, nextPage) => {
                    void loadList(nextPage);
                  }}
                  sx={{ '.MuiTablePagination-toolbar': { minHeight: 42 } }}
                />
              </>
            ) : null}
          </CardContent>
        </Card>

        <Stack spacing={1.6} sx={{ minHeight: 0 }}>
          <Card variant="outlined">
            <CardContent sx={{ py: 1.7 }}>
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                선택 Window 요약
              </Typography>
              <Divider sx={{ my: 1 }} />

              {!selectedWindow ? (
                <Alert severity="info">Window를 선택하면 원인 후보 요약을 확인할 수 있습니다.</Alert>
              ) : (
                <Stack spacing={1.05}>
                  <Typography variant="body2" color="text.secondary">
                    설비: {selectedEquipmentDisplayText} / 데이터셋: {selectedDatasetKey || '-'} / 실행:{' '}
                    {selectedRun?.runId ?? '-'}
                  </Typography>
                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      선택 시간 (Window)
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.25 }}>
                      {formatDateTime(detail?.windowStart ?? selectedWindow.windowStart)} ~{' '}
                      {formatDateTime(detail?.windowEnd ?? selectedWindow.windowEnd)}
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 0.7 }}>
                    {badge(currentStatusStyle.label, currentStatusStyle.color, currentStatusStyle.backgroundColor)}
                    <Typography variant="body2">
                      이상 점수 <b>{formatAnomalyScore(detail?.anomalyScore ?? selectedWindow.anomalyScore)}</b>
                    </Typography>
                    <Typography variant="body2">
                      건강도 지수 <b>{formatHealthIndex(detail?.healthIndex ?? selectedWindow.healthIndex)}</b>
                    </Typography>
                  </Stack>

                  <Box>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 0.4 }}>
                      주요 상위 후보 그룹
                    </Typography>
                    <Stack direction="row" spacing={0.6} sx={{ flexWrap: 'wrap', rowGap: 0.6 }}>
                      {summaryGroups.length > 0
                        ? summaryGroups.map((groupLabel) =>
                            badge(groupLabel, '#0d47a1', '#e3f2fd', `summary-${groupLabel}`),
                          )
                        : badge('원인 후보 미생성', '#455a64', '#eceff1')}
                    </Stack>
                  </Box>

                  {loadingDetail ? (
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ pt: 0.4 }}>
                      <CircularProgress size={18} />
                      <Typography variant="body2" color="text.secondary">
                        선택 Window 상세를 불러오는 중입니다.
                      </Typography>
                    </Stack>
                  ) : null}

                  {!loadingDetail && !selectedWindow.causeGenerated ? (
                    <Alert severity="info" sx={{ py: 0.5 }}>
                      선택한 window는 현재 원인 후보 미생성 상태입니다.
                    </Alert>
                  ) : null}

                  {!loadingDetail && selectedWindow.causeGenerated && detail ? (
                    <Typography variant="body2" color="text.secondary">
                      cause_method: {detail.causeMethod ?? '-'} | cause_version: {detail.causeVersion ?? '-'}
                    </Typography>
                  ) : null}
                </Stack>
              )}
            </CardContent>
          </Card>

          <Box
            sx={{
              display: 'grid',
              gap: 1.6,
              gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
              alignItems: 'stretch',
            }}
          >
            <Card variant="outlined" sx={{ minHeight: 255 }}>
              <CardContent sx={{ py: 1.5 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                  주요 상위 후보 Top 3
                </Typography>
                <Divider sx={{ my: 0.9 }} />

                {!selectedWindow ? (
                  <Alert severity="info">Window를 선택하면 주요 상위 후보 지표를 볼 수 있습니다.</Alert>
                ) : null}

                {selectedWindow && !selectedWindow.causeGenerated ? (
                  <Alert severity="info">선택한 window는 현재 원인 후보 미생성 상태입니다.</Alert>
                ) : null}

                {selectedWindow && selectedWindow.causeGenerated && !loadingDetail && top3Candidates.length === 0 ? (
                  <Alert severity="info">원인 후보 지표가 없습니다.</Alert>
                ) : null}

                {selectedWindow && selectedWindow.causeGenerated && top3Candidates.length > 0 ? (
                  <Stack spacing={0.85}>
                    {top3Candidates.map((candidate, index) => {
                      const directionLabel = toDirectionLabel(candidate.direction);
                      const directionColor = toDirectionColor(candidate.direction);
                      return (
                        <Box
                          key={`top3-${candidate.rank ?? index}-${candidate.sourceField ?? candidate.feature ?? index}`}
                          sx={{
                            p: 0.9,
                            border: '1px solid #e0e7f1',
                            borderRadius: 1.2,
                            backgroundColor: '#fff',
                          }}
                        >
                          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
                            <Stack direction="row" spacing={0.7} alignItems="center" sx={{ minWidth: 0 }}>
                              {badge(String(candidate.rank ?? index + 1), '#ffffff', '#1976d2')}
                              <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap>
                                {candidate.displayName}
                              </Typography>
                              {badge(candidate.causeGroup, '#5d4037', '#fbe9e7')}
                            </Stack>
                            <Typography variant="caption" sx={{ color: directionColor, fontWeight: 800, whiteSpace: 'nowrap' }}>
                              {directionLabel}
                            </Typography>
                          </Stack>

                          <Typography variant="caption" color="text.secondary" sx={{ mt: 0.45, display: 'block' }}>
                            편차 {formatDecimal(candidate.deviationScore, 2)} | 현재값{' '}
                            {formatDecimal(candidate.currentValue, 2)}
                            {candidate.unit ? ` ${candidate.unit}` : ''} | 기준 평균{' '}
                            {formatDecimal(candidate.baselineMean, 2)}
                            {candidate.unit ? ` ${candidate.unit}` : ''}
                          </Typography>
                        </Box>
                      );
                    })}
                  </Stack>
                ) : null}
              </CardContent>
            </Card>

            <Card variant="outlined" sx={{ minHeight: 255 }}>
              <CardContent sx={{ py: 1.5 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                  원인 후보 그룹 점수표
                </Typography>
                <Divider sx={{ my: 0.9 }} />

                {!selectedWindow ? (
                  <Alert severity="info">Window를 선택하면 원인 후보 그룹 점수표를 볼 수 있습니다.</Alert>
                ) : null}

                {selectedWindow && selectedWindow.causeGenerated && !loadingDetail && groupScoreRows.length === 0 ? (
                  <Alert severity="info">원인 후보 그룹 점수표 데이터가 없습니다.</Alert>
                ) : null}

                {groupScoreRows.length > 0 ? (
                  <Stack spacing={1}>
                    {groupScoreRows.slice(0, 5).map((groupScore) => {
                      const score = groupScore.score ?? 0;
                      const ratio = Math.max(0, Math.min(100, (score / maxGroupScore) * 100));
                      const barColor = GROUP_COLORS[groupScore.causeGroup] ?? '#546e7a';

                      return (
                        <Box key={`group-score-${groupScore.rank}-${groupScore.causeGroup}`}>
                          <Stack direction="row" justifyContent="space-between" alignItems="center">
                            <Typography variant="body2" sx={{ fontWeight: 800 }}>
                              {groupScore.causeGroup}
                            </Typography>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {formatDecimal(groupScore.score, 2)}
                            </Typography>
                          </Stack>
                          <Box
                            sx={{
                              mt: 0.35,
                              width: '100%',
                              height: 9,
                              borderRadius: 999,
                              backgroundColor: '#eceff1',
                              overflow: 'hidden',
                            }}
                          >
                            <Box
                              sx={{
                                width: `${ratio}%`,
                                height: '100%',
                                backgroundColor: barColor,
                                transition: 'width 0.25s ease',
                              }}
                            />
                          </Box>
                        </Box>
                      );
                    })}
                  </Stack>
                ) : null}
              </CardContent>
            </Card>
          </Box>
        </Stack>
      </Box>

      <Card variant="outlined">
        <CardContent sx={{ py: 1.7 }}>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            원인 후보 상세 목록
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.3 }}>
            원인 후보 지표는 정상 기준 대비 편차를 기준으로 표시합니다.
          </Typography>
          <Divider sx={{ my: 1 }} />

          {!selectedWindow ? (
            <Alert severity="info">Window를 선택하면 원인 후보 상세 목록을 확인할 수 있습니다.</Alert>
          ) : null}

          {selectedWindow && !selectedWindow.causeGenerated ? (
            <Alert severity="info">선택한 window는 현재 원인 후보 미생성 상태입니다.</Alert>
          ) : null}

          {selectedWindow && selectedWindow.causeGenerated && candidateRows.length === 0 && !loadingDetail ? (
            <Alert severity="info">원인 후보 상세 데이터가 없습니다.</Alert>
          ) : null}

          {candidateRows.length > 0 ? (
            <TableContainer sx={{ border: '1px solid #d7e0ee', borderRadius: 1, maxHeight: 320 }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 800, width: 58 }}>순위</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 170 }}>후보</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 150 }}>원본 feature</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 80 }}>그룹</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 70 }}>통계</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 90 }}>현재값</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 95 }}>기준 평균</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 105 }}>기준 표준편차</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 85 }}>편차 점수</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 88 }}>방향</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 210 }}>Description</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {candidateRows.map((candidate, index) => (
                    <TableRow key={`detail-${candidate.rank ?? index}-${candidate.sourceField ?? candidate.feature ?? index}`}>
                      <TableCell>{candidate.rank ?? index + 1}</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>{candidate.displayName}</TableCell>
                      <TableCell>{candidate.sourceField ?? candidate.feature ?? '-'}</TableCell>
                      <TableCell>{badge(candidate.causeGroup, '#5d4037', '#fbe9e7')}</TableCell>
                      <TableCell>{candidate.stat ?? '-'}</TableCell>
                      <TableCell>
                        {formatDecimal(candidate.currentValue, 2)}
                        {candidate.unit ? ` ${candidate.unit}` : ''}
                      </TableCell>
                      <TableCell>
                        {formatDecimal(candidate.baselineMean, 2)}
                        {candidate.unit ? ` ${candidate.unit}` : ''}
                      </TableCell>
                      <TableCell>{formatDecimal(candidate.baselineStd, 2)}</TableCell>
                      <TableCell sx={{ fontWeight: 800 }}>{formatDecimal(candidate.deviationScore, 2)}</TableCell>
                      <TableCell sx={{ color: toDirectionColor(candidate.direction), fontWeight: 800 }}>
                        {toDirectionLabel(candidate.direction)}
                      </TableCell>
                      <TableCell>{candidate.reasonText ?? '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : null}
        </CardContent>
      </Card>
    </Stack>
  );
}
