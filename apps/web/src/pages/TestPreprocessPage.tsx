import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Collapse,
  Divider,
  FormControlLabel,
  FormGroup,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';
import FolderIcon from '@mui/icons-material/Folder';
import FolderOpenIcon from '@mui/icons-material/FolderOpen';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { preprocessService } from '../services/preprocessService';
import { persistOperationDatasetKey } from '../utils/operationDataset';
import type {
  DataSourceDataset,
  DataSourceType,
  FeatureAutoJobUpsertRequest,
  FeatureAutoTriggerRequest,
  FeatureAutoJobListData,
  FeatureAutoJobStatus,
  FeaturePreviewData,
  FeaturePreviewRow,
  RawDataPreviewData,
} from '../types/preprocess';

const RAW_PREVIEW_LIMIT = 200;
const FEATURE_PREVIEW_LIMIT = 30;
const FEATURE_RENDER_SAMPLE_LIMIT = 20;
const DEFAULT_WINDOW_SIZE = 100;
const PREPROCESS_TREE_STORAGE_KEY = 'demo_preprocess_tree_state_v1';

type PreprocessTreePersistState = {
  selectedTypeCode?: string;
  selectedDtlCode?: string;
  selectedDatasetKey?: string;
  expandedTypeCode?: string | null;
  expandedEquipmentGroups?: string[];
};

function loadPreprocessTreeState(): PreprocessTreePersistState {
  try {
    const saved = window.localStorage.getItem(PREPROCESS_TREE_STORAGE_KEY);
    if (!saved) {
      return {};
    }
    return JSON.parse(saved) as PreprocessTreePersistState;
  } catch (_error) {
    return {};
  }
}

const FEATURE_BASE_COLUMNS = [
  'window_start',
  'window_end',
  'equipment_id',
  'lot_no',
  'SOURCE_TYPE_CODE',
  'SOURCE_DTL_CODE',
  'SOURCE_FILE',
  'REG_DATE',
] as const;

const FEATURE_STAT_COLUMNS = ['MEAN', 'STD', 'MIN', 'MAX'] as const;

type DataViewMode = 'raw' | 'feature';

const FEATURE_DISABLED_GUIDE_MESSAGE = `이 dataset은 지도학습용 라벨 원본 데이터입니다.
현재 단계에서는 Feature 자동 생성 대상이 아니며,
Random Forest 학습 시 원본 row 데이터를 직접 사용합니다.`;

const SUPERVISED_DATASET_GUIDE_MESSAGE = `라벨링/지도학습용 데이터셋입니다.
THISHMIDATA 원본 row를 조회하면서 label 컬럼 또는 ANAL_STAT 기반 자동 라벨을 확인하는 용도입니다.
Feature 자동 생성/비지도 학습 실행은 운영용 FEATURE_SOURCE 데이터셋에서만 수행합니다.`;

const FEATURE_SOURCE_GUIDE_MESSAGE = `비지도 학습/운영용 데이터셋입니다.
THISHMIDATA 원본을 설비별 조건으로 조회하고, 선택한 숫자형 컬럼으로 feature window를 생성합니다.`;

type SourceDatasetOption = {
  label: string;
  dataset: DataSourceDataset;
};

type PreviewMetric = {
  requestMs: number;
  responseBytes: number;
  renderMs: number | null;
  rowCount: number;
  renderedRowCount: number;
};

// ── 트리 그룹핑용 타입 ──

type DatasetEquipmentGroup = {
  groupKey: string;
  groupName: string;
  datasets: DataSourceDataset[];
};

type DatasetPurposeGroup = {
  purposeKey: string;
  purposeLabel: string;
  chipColor: 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'info' | 'error';
  equipmentGroups: DatasetEquipmentGroup[];
};

// ── PURPOSE 표시 순서: 비지도/운영 먼저, 라벨링/지도학습 나중 ──
const PURPOSE_SORT_ORDER: Record<string, number> = {
  FEATURE_SOURCE: 0,
  SUPERVISED_SOURCE: 1,
};

function groupDatasetsByPurposeAndEquipment(datasets: DataSourceDataset[]): DatasetPurposeGroup[] {
  const purposeMap = new Map<string, Map<string, DataSourceDataset[]>>();

  for (const dataset of datasets) {
    const purposeKey = normalizeOptionalText(dataset.datasetPurpose)?.toUpperCase() ?? 'FEATURE_SOURCE';
    const equipGroupKey = normalizeOptionalText(dataset.equipmentGroup) ?? '__UNGROUPED__';

    if (!purposeMap.has(purposeKey)) {
      purposeMap.set(purposeKey, new Map());
    }
    const equipMap = purposeMap.get(purposeKey)!;
    if (!equipMap.has(equipGroupKey)) {
      equipMap.set(equipGroupKey, []);
    }
    equipMap.get(equipGroupKey)!.push(dataset);
  }

  const purposeGroups: DatasetPurposeGroup[] = [];

  for (const [purposeKey, equipMap] of purposeMap.entries()) {
    const equipmentGroups: DatasetEquipmentGroup[] = [];

    for (const [groupKey, groupDatasets] of equipMap.entries()) {
      const firstName = groupDatasets[0];
      const groupName =
        groupKey === '__UNGROUPED__'
          ? '기타'
          : normalizeOptionalText(firstName?.equipmentGroupName) ?? groupKey;

      equipmentGroups.push({
        groupKey,
        groupName,
        datasets: groupDatasets,
      });
    }

    equipmentGroups.sort((a, b) => a.groupName.localeCompare(b.groupName));

    let purposeLabel: string;
    let chipColor: DatasetPurposeGroup['chipColor'];
    if (purposeKey === 'SUPERVISED_SOURCE') {
      purposeLabel = '라벨링/지도학습';
      chipColor = 'warning';
    } else if (purposeKey === 'FEATURE_SOURCE') {
      purposeLabel = '비지도/운영';
      chipColor = 'success';
    } else {
      purposeLabel = purposeKey;
      chipColor = 'default';
    }

    purposeGroups.push({
      purposeKey,
      purposeLabel,
      chipColor,
      equipmentGroups,
    });
  }

  purposeGroups.sort(
    (a, b) => (PURPOSE_SORT_ORDER[a.purposeKey] ?? 99) - (PURPOSE_SORT_ORDER[b.purposeKey] ?? 99),
  );

  return purposeGroups;
}

function buildEquipmentGroupToggleKey(
  typeCode: string,
  dtlCode: string,
  purposeKey: string,
  equipGroupKey: string,
): string {
  return `${typeCode}::${dtlCode}::${purposeKey}::${equipGroupKey}`;
}

function resolveEquipmentGroupToggleKeyForDataset(
  typeCode: string,
  dtlCode: string,
  dataset: DataSourceDataset,
): string {
  const purposeKey = normalizeOptionalText(dataset.datasetPurpose)?.toUpperCase() ?? 'FEATURE_SOURCE';
  const equipGroupKey = normalizeOptionalText(dataset.equipmentGroup) ?? '__UNGROUPED__';
  return buildEquipmentGroupToggleKey(typeCode, dtlCode, purposeKey, equipGroupKey);
}

// ── 유틸리티 함수 ──

function formatCellValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '-';
  }

  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }

  try {
    return JSON.stringify(value);
  } catch (_error) {
    return String(value);
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function extractFeatureMetricValue(row: FeaturePreviewRow, column: string): unknown {
  if (!column.includes('.')) {
    return row[column];
  }

  const [stat, metric] = column.split('.');
  const featureValues = row.feature_values;
  if (!isRecord(featureValues)) {
    return null;
  }

  const statValues = featureValues[stat];
  if (!isRecord(statValues)) {
    return null;
  }

  return statValues[metric];
}

function estimatePayloadBytes(data: unknown): number {
  try {
    return new Blob([JSON.stringify(data)]).size;
  } catch (_error) {
    return 0;
  }
}

function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }
  if (bytes >= 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${bytes} B`;
}

function formatDuration(milliseconds: number | null): string {
  if (milliseconds === null) {
    return '-';
  }
  return `${milliseconds.toFixed(1)} ms`;
}

function toUtcStartIsoFromKstDate(dateText: string): string | null {
  const normalized = dateText.trim();
  if (!normalized) {
    return null;
  }
  const parsedDate = new Date(`${normalized}T00:00:00+09:00`);
  if (Number.isNaN(parsedDate.getTime())) {
    return null;
  }
  return parsedDate.toISOString();
}

function toUtcEndIsoFromKstDate(dateText: string): string | null {
  const normalized = dateText.trim();
  if (!normalized) {
    return null;
  }
  const nextDay = new Date(`${normalized}T00:00:00+09:00`);
  if (Number.isNaN(nextDay.getTime())) {
    return null;
  }
  nextDay.setDate(nextDay.getDate() + 1);
  return new Date(nextDay.getTime() - 1).toISOString();
}

function getDatasetPurpose(dataset: DataSourceDataset | null): string {
  return normalizeOptionalText(dataset?.datasetPurpose)?.toUpperCase() ?? 'FEATURE_SOURCE';
}

function isSupervisedSourceDataset(dataset: DataSourceDataset | null): boolean {
  return getDatasetPurpose(dataset) === 'SUPERVISED_SOURCE';
}

function isFeatureSourceDataset(dataset: DataSourceDataset | null): boolean {
  return getDatasetPurpose(dataset) === 'FEATURE_SOURCE';
}

function isFeatureEnabledDataset(dataset: DataSourceDataset | null): boolean {
  if (!dataset) {
    return true;
  }

  return isFeatureSourceDataset(dataset) && dataset.featureEnabled !== false;
}

function buildDatasetPurposeLabel(dataset: DataSourceDataset | null): string {
  const purpose = getDatasetPurpose(dataset);
  if (purpose === 'SUPERVISED_SOURCE') {
    return '라벨링/지도학습';
  }
  if (purpose === 'FEATURE_SOURCE') {
    return '비지도/운영';
  }
  return purpose;
}

function getDatasetPurposeChipColor(
  dataset: DataSourceDataset | null,
): 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'info' | 'error' {
  if (isSupervisedSourceDataset(dataset)) {
    return 'warning';
  }
  if (isFeatureSourceDataset(dataset)) {
    return 'success';
  }
  return 'default';
}

export function TestPreprocessPage() {
  const navigate = useNavigate();

  const [dataSources, setDataSources] = useState<DataSourceType[]>([]);
  const persistedTreeState = useMemo(() => loadPreprocessTreeState(), []);

  const [selectedTypeCode, setSelectedTypeCode] = useState(persistedTreeState.selectedTypeCode ?? '');
  const [selectedDtlCode, setSelectedDtlCode] = useState(persistedTreeState.selectedDtlCode ?? '');
  const [expandedTypeCode, setExpandedTypeCode] = useState<string | null>(
    persistedTreeState.expandedTypeCode ?? null,
  );
  const [expandedEquipmentGroups, setExpandedEquipmentGroups] = useState<Set<string>>(
    () => new Set(persistedTreeState.expandedEquipmentGroups ?? []),
  );

  const [previewData, setPreviewData] = useState<RawDataPreviewData | null>(null);
  const [selectedColumns, setSelectedColumns] = useState<string[]>([]);
  const [selectedDatasetKey, setSelectedDatasetKey] = useState(persistedTreeState.selectedDatasetKey ?? '');
  const [rawPreviewFromDate, setRawPreviewFromDate] = useState('');
  const [rawPreviewToDate, setRawPreviewToDate] = useState('');
  const [rawPreviewEquipmentId, setRawPreviewEquipmentId] = useState('');
  const [activeDataset, setActiveDataset] = useState<DataSourceDataset | null>(null);

  const [featureData, setFeatureData] = useState<FeaturePreviewData | null>(null);
  const [dataViewMode, setDataViewMode] = useState<DataViewMode>('raw');
  const [windowSize, setWindowSize] = useState(DEFAULT_WINDOW_SIZE);
  const [autoJobData, setAutoJobData] = useState<FeatureAutoJobListData | null>(null);

  const [loadingSources, setLoadingSources] = useState(false);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [loadingFeature, setLoadingFeature] = useState(false);
  const [loadingAutoJobs, setLoadingAutoJobs] = useState(false);
  const [savingAutoPolicy, setSavingAutoPolicy] = useState(false);
  const [triggeringAutoJob, setTriggeringAutoJob] = useState(false);

  const [sourceError, setSourceError] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [featureError, setFeatureError] = useState<string | null>(null);
  const [autoJobError, setAutoJobError] = useState<string | null>(null);
  const [autoActionError, setAutoActionError] = useState<string | null>(null);
  const [autoActionMessage, setAutoActionMessage] = useState<string | null>(null);
  const [rawPreviewMetric, setRawPreviewMetric] = useState<PreviewMetric | null>(null);
  const [featurePreviewMetric, setFeaturePreviewMetric] = useState<PreviewMetric | null>(null);

  const loadedFeatureDatasetTokenRef = useRef<string>('');
  const rawRenderMeasureStartRef = useRef<number | null>(null);
  const featureRenderMeasureStartRef = useRef<number | null>(null);
  const syncedPolicyTokenRef = useRef<string>('');

  useEffect(() => {
    window.localStorage.setItem(
      PREPROCESS_TREE_STORAGE_KEY,
      JSON.stringify({
        selectedTypeCode,
        selectedDtlCode,
        selectedDatasetKey,
        expandedTypeCode,
        expandedEquipmentGroups: Array.from(expandedEquipmentGroups),
      }),
    );
  }, [selectedTypeCode, selectedDtlCode, selectedDatasetKey, expandedTypeCode, expandedEquipmentGroups]);

  // ── 설비그룹 토글 ──

  const toggleEquipmentGroup = (toggleKey: string) => {
    setExpandedEquipmentGroups((previous) => {
      const next = new Set(previous);
      if (next.has(toggleKey)) {
        next.delete(toggleKey);
      } else {
        next.add(toggleKey);
      }
      return next;
    });
  };

  const ensureEquipmentGroupExpanded = (toggleKey: string) => {
    setExpandedEquipmentGroups((previous) => {
      if (previous.has(toggleKey)) {
        return previous;
      }
      const next = new Set(previous);
      next.add(toggleKey);
      return next;
    });
  };

  // ── 데이터 로드 ──

  const loadFeatureAutoJobs = useCallback(async (silent = false) => {
    if (!silent) {
      setLoadingAutoJobs(true);
    }
    setAutoJobError(null);

    try {
      const data = await preprocessService.getFeatureAutoJobs();
      setAutoJobData(data);
    } catch (error: unknown) {
      setAutoJobError(error instanceof Error ? error.message : '자동 feature job 상태를 불러오지 못했습니다.');
      setAutoJobData(null);
    } finally {
      if (!silent) {
        setLoadingAutoJobs(false);
      }
    }
  }, []);

  useEffect(() => {
    setLoadingSources(true);
    setSourceError(null);

    preprocessService
      .getDataSources()
      .then((data) => {
        const dataTypes = data.dataTypes ?? [];
        setDataSources(dataTypes);

        const restoredDatasetNode = findDatasetNodeByKey(dataTypes, persistedTreeState.selectedDatasetKey);

        if (restoredDatasetNode) {
          setSelectedTypeCode(restoredDatasetNode.typeCode);
          setSelectedDtlCode(restoredDatasetNode.dtlCode);
          setSelectedDatasetKey(restoredDatasetNode.dataset.datasetKey);

          if (persistedTreeState.expandedTypeCode !== undefined) {
            setExpandedTypeCode(persistedTreeState.expandedTypeCode);
          }

          if (persistedTreeState.expandedEquipmentGroups) {
            setExpandedEquipmentGroups(new Set(persistedTreeState.expandedEquipmentGroups));
          }

          return;
        }

        const firstDatasetNode = findFirstDatasetNode(dataTypes);
        if (firstDatasetNode) {
          setSelectedTypeCode(firstDatasetNode.typeCode);
          setSelectedDtlCode(firstDatasetNode.dtlCode);
          setSelectedDatasetKey(firstDatasetNode.dataset.datasetKey);
          setExpandedTypeCode(firstDatasetNode.typeCode);

          const toggleKey = resolveEquipmentGroupToggleKeyForDataset(
            firstDatasetNode.typeCode,
            firstDatasetNode.dtlCode,
            firstDatasetNode.dataset,
          );
          setExpandedEquipmentGroups(new Set([toggleKey]));
          return;
        }

        setSelectedTypeCode('');
        setSelectedDtlCode('');
        setSelectedDatasetKey('');
        setExpandedTypeCode(null);
      })
      .catch((error: unknown) => {
        setSourceError(error instanceof Error ? error.message : '데이터 소스 목록을 불러오지 못했습니다.');
      })
      .finally(() => {
        setLoadingSources(false);
      });
  }, [persistedTreeState]);

  useEffect(() => {
    void loadFeatureAutoJobs();

    const intervalId = window.setInterval(() => {
      void loadFeatureAutoJobs(true);
    }, 15000);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [loadFeatureAutoJobs]);

  useEffect(() => {
    setLoadingPreview(true);
    setPreviewError(null);
    const requestStartedAt = performance.now();
    const fromIso = toUtcStartIsoFromKstDate(rawPreviewFromDate);
    const toIso = toUtcEndIsoFromKstDate(rawPreviewToDate);

    preprocessService
      .getRawPreview(
        selectedDatasetKey.trim().length > 0
          ? {
              datasetKey: selectedDatasetKey,
              from: fromIso,
              to: toIso,
              equipmentId: rawPreviewEquipmentId.trim(),
              limit: RAW_PREVIEW_LIMIT,
            }
          : {
              typeCode: selectedTypeCode,
              dtlCode: selectedDtlCode,
              from: fromIso,
              to: toIso,
              equipmentId: rawPreviewEquipmentId.trim(),
              limit: RAW_PREVIEW_LIMIT,
            },
      )
      .then((data) => {
        const requestMs = performance.now() - requestStartedAt;
        const responseBytes = estimatePayloadBytes(data);
        rawRenderMeasureStartRef.current = performance.now();

        setPreviewData(data);
        setRawPreviewMetric({
          requestMs,
          responseBytes,
          renderMs: null,
          rowCount: data.rawRows.length,
          renderedRowCount: data.rawRows.length,
        });
        setSelectedColumns((previousColumns) => {
          const previousInRange = previousColumns.filter((column) => data.availableColumns.includes(column));
          return previousInRange.length > 0 ? previousInRange : data.availableColumns;
        });
      })
      .catch((error: unknown) => {
        setPreviewError(error instanceof Error ? error.message : '원본 데이터 미리보기를 불러오지 못했습니다.');
        setPreviewData(null);
        setSelectedColumns([]);
        setRawPreviewMetric(null);
      })
      .finally(() => {
        setLoadingPreview(false);
      });
  }, [selectedDatasetKey, selectedTypeCode, selectedDtlCode, rawPreviewFromDate, rawPreviewToDate, rawPreviewEquipmentId]);

  const hasSourceSelection =
    selectedDatasetKey.trim().length > 0 || selectedTypeCode.trim().length > 0 || selectedDtlCode.trim().length > 0;

  useEffect(() => {
    const normalizedDatasetKey = selectedDatasetKey.trim();
    if (!normalizedDatasetKey) {
      return;
    }
    persistOperationDatasetKey(normalizedDatasetKey);
  }, [selectedDatasetKey]);

  const selectedDetail = useMemo(() => {
    const type = dataSources.find((item) => item.typeCode === selectedTypeCode);
    if (!type) {
      return null;
    }

    const detail = type.details.find((item) => item.dtlCode === selectedDtlCode);
    if (!detail) {
      return null;
    }

    return { type, detail };
  }, [dataSources, selectedTypeCode, selectedDtlCode]);

  const visibleColumns = useMemo(() => {
    if (!previewData) {
      return [] as string[];
    }

    return previewData.availableColumns.filter((column) => selectedColumns.includes(column));
  }, [previewData, selectedColumns]);

  useEffect(() => {
    if (!previewData || rawRenderMeasureStartRef.current === null) {
      return;
    }

    const renderStartedAt = rawRenderMeasureStartRef.current;
    const frameId = window.requestAnimationFrame(() => {
      setRawPreviewMetric((previous) => {
        if (!previous) {
          return previous;
        }
        return {
          ...previous,
          renderMs: performance.now() - renderStartedAt,
          renderedRowCount: previewData.rawRows.length,
        };
      });
      rawRenderMeasureStartRef.current = null;
    });

    return () => {
      window.cancelAnimationFrame(frameId);
    };
  }, [previewData, visibleColumns.length]);

  const selectedFeatureColumns = useMemo(() => {
    if (!previewData) {
      return [] as string[];
    }
    return selectedColumns.filter((column) => previewData.numericColumns.includes(column));
  }, [previewData, selectedColumns]);

  const sourceDatasetOptions = useMemo<SourceDatasetOption[]>(() => {
    if (!selectedDetail) {
      return [];
    }
    return (selectedDetail.detail.datasets ?? []).map((dataset) => ({
      label: buildDatasetOptionLabel(dataset),
      dataset,
    }));
  }, [selectedDetail]);

  useEffect(() => {
    if (sourceDatasetOptions.length === 0) {
      if (selectedDatasetKey !== '') {
        setSelectedDatasetKey('');
      }
      return;
    }

    const selectedExists = sourceDatasetOptions.some((option) => option.dataset.datasetKey === selectedDatasetKey);
    if (!selectedExists) {
      setSelectedDatasetKey(sourceDatasetOptions[0].dataset.datasetKey);
    }
  }, [selectedDatasetKey, sourceDatasetOptions]);

  const selectedSourceDatasetOption = useMemo<SourceDatasetOption | null>(() => {
    if (sourceDatasetOptions.length === 0) {
      return null;
    }

    return sourceDatasetOptions.find((option) => option.dataset.datasetKey === selectedDatasetKey) ?? sourceDatasetOptions[0];
  }, [selectedDatasetKey, sourceDatasetOptions]);

  useEffect(() => {
    setActiveDataset(selectedSourceDatasetOption?.dataset ?? null);
  }, [selectedSourceDatasetOption]);

  const featureTargetCount = sourceDatasetOptions.length;
  const isFeatureSource = isFeatureSourceDataset(activeDataset);
  const isSupervisedSource = isSupervisedSourceDataset(activeDataset);
  const featureEnabled = isFeatureEnabledDataset(activeDataset);
  const effectiveDatasetToken = activeDataset?.datasetKey ?? null;
  const effectivePolicyDatasetKey = featureEnabled ? normalizeDatasetKeyString(activeDataset?.datasetKey) : null;
  const effectivePolicyDatasetName =
    activeDataset?.datasetName ??
    activeDataset?.sourceCollection ??
    'THISHMIDATA';
  const effectiveDatasetLabel = buildDatasetStatusLabel(activeDataset);
  const activeLabelField = normalizeOptionalText(activeDataset?.labelField);
  const featureAutoJobs = autoJobData?.jobs ?? [];
  const schedulerFixedDelaySeconds = autoJobData
    ? Math.max(1, Math.floor(autoJobData.scheduler_fixed_delay_ms / 1000))
    : 300;

  const selectedAutoJob = useMemo<FeatureAutoJobStatus | null>(() => {
    if (!effectivePolicyDatasetKey) {
      return null;
    }

    const currentDatasetKey = normalizeDatasetKeyString(effectivePolicyDatasetKey);
    if (!currentDatasetKey) {
      return null;
    }
    return (
      featureAutoJobs.find((job) => normalizeDatasetKeyString(job.dataset_key) === currentDatasetKey) ?? null
    );
  }, [effectivePolicyDatasetKey, featureAutoJobs]);

  useEffect(() => {
    if (!featureEnabled && dataViewMode === 'feature') {
      setDataViewMode('raw');
    }
  }, [dataViewMode, featureEnabled]);

  useEffect(() => {
    if (featureEnabled) {
      return;
    }
    setFeatureData(null);
    setFeatureError(null);
    setFeaturePreviewMetric(null);
    loadedFeatureDatasetTokenRef.current = '';
  }, [featureEnabled]);

  const autoPolicyPayloadPreview = useMemo<FeatureAutoJobUpsertRequest | null>(() => {
    if (!effectivePolicyDatasetKey) {
      return null;
    }
    if (selectedFeatureColumns.length === 0) {
      return null;
    }
    if (!Number.isFinite(windowSize) || windowSize <= 0) {
      return null;
    }

    const payload: FeatureAutoJobUpsertRequest = {
      dataset_name: effectivePolicyDatasetName,
      dataset_key: effectivePolicyDatasetKey,
      selected_columns: selectedFeatureColumns,
      window_size: windowSize,
      target_collection: selectedAutoJob?.target_collection ?? 'thisfeature',
      schedule_interval_seconds: selectedAutoJob?.schedule_interval_seconds ?? schedulerFixedDelaySeconds,
      use_yn: true,
    };

    return payload;
  }, [
    effectivePolicyDatasetKey,
    effectivePolicyDatasetName,
    schedulerFixedDelaySeconds,
    selectedAutoJob,
    selectedFeatureColumns,
    windowSize,
  ]);

  const autoTriggerPayloadPreview = useMemo<FeatureAutoTriggerRequest | null>(() => {
    if (!effectivePolicyDatasetKey) {
      return null;
    }

    const payload: FeatureAutoTriggerRequest = {
      dataset_key: effectivePolicyDatasetKey,
    };

    return payload;
  }, [effectivePolicyDatasetKey]);

  useEffect(() => {
    if (!effectiveDatasetToken || !selectedAutoJob || syncedPolicyTokenRef.current === effectiveDatasetToken) {
      return;
    }

    if (selectedAutoJob.window_size > 0) {
      setWindowSize(selectedAutoJob.window_size);
    }

    if (previewData && selectedAutoJob.selected_columns.length > 0) {
      const selectedColumnSet = new Set(selectedAutoJob.selected_columns);
      const policyColumns = previewData.availableColumns.filter((column) => selectedColumnSet.has(column));
      if (policyColumns.length > 0) {
        setSelectedColumns(policyColumns);
      }
    }

    syncedPolicyTokenRef.current = effectiveDatasetToken;
  }, [effectiveDatasetToken, selectedAutoJob, previewData]);

  useEffect(() => {
    if (!selectedAutoJob) {
      syncedPolicyTokenRef.current = '';
    }
  }, [selectedAutoJob]);

  const loadFeaturePreview = useCallback(async (datasetKey: string, datasetToken: string) => {
    setLoadingFeature(true);
    setFeatureError(null);
    const requestStartedAt = performance.now();

    try {
      const data = await preprocessService.getFeaturePreview(datasetKey, FEATURE_PREVIEW_LIMIT, true);
      const requestMs = performance.now() - requestStartedAt;
      const responseBytes = estimatePayloadBytes(data);
      const renderedRowCount = Math.min(data.featureRows.length, FEATURE_RENDER_SAMPLE_LIMIT);

      setFeatureData(data);
      loadedFeatureDatasetTokenRef.current = datasetToken;
      featureRenderMeasureStartRef.current = performance.now();
      setFeaturePreviewMetric({
        requestMs,
        responseBytes,
        renderMs: null,
        rowCount: data.featureRows.length,
        renderedRowCount,
      });
    } catch (error: unknown) {
      setFeatureError(error instanceof Error ? error.message : 'Feature 데이터를 불러오지 못했습니다.');
      setFeatureData(null);
      loadedFeatureDatasetTokenRef.current = '';
      setFeaturePreviewMetric(null);
    } finally {
      setLoadingFeature(false);
    }
  }, []);

  const saveAutoPolicy = useCallback(async () => {
    if (!featureEnabled) {
      setAutoActionError('현재 선택한 dataset은 Feature 자동 생성 대상이 아닙니다.');
      return;
    }

    if (!effectivePolicyDatasetKey) {
      setAutoActionError('자동 생성 정책 대상 dataset_key를 찾을 수 없습니다.');
      return;
    }

    if (selectedFeatureColumns.length === 0) {
      setAutoActionError('자동 생성 정책용 숫자형 컬럼을 1개 이상 선택하세요.');
      return;
    }

    if (!Number.isFinite(windowSize) || windowSize <= 0) {
      setAutoActionError('window size는 1 이상의 정수여야 합니다.');
      return;
    }

    setSavingAutoPolicy(true);
    setAutoActionError(null);
    setAutoActionMessage(null);

    try {
      if (!autoPolicyPayloadPreview) {
        setAutoActionError('자동 생성 정책 저장 Payload를 구성할 수 없습니다.');
        return;
      }

      await preprocessService.upsertFeatureAutoJob(autoPolicyPayloadPreview);

      setAutoActionMessage('자동 feature 생성 정책이 저장되었습니다.');
      await loadFeatureAutoJobs();
    } catch (error: unknown) {
      setAutoActionError(error instanceof Error ? error.message : '자동 feature 생성 정책 저장에 실패했습니다.');
    } finally {
      setSavingAutoPolicy(false);
    }
  }, [
    autoPolicyPayloadPreview,
    effectivePolicyDatasetKey,
    featureEnabled,
    loadFeatureAutoJobs,
    selectedFeatureColumns,
    windowSize,
  ]);

  const triggerAutoGenerationNow = useCallback(async () => {
    if (!featureEnabled) {
      setAutoActionError('현재 선택한 dataset은 Feature 자동 생성 대상이 아닙니다.');
      return;
    }

    if (!effectivePolicyDatasetKey) {
      setAutoActionError('수동 트리거 대상 dataset_key를 찾을 수 없습니다.');
      return;
    }

    setTriggeringAutoJob(true);
    setAutoActionError(null);
    setAutoActionMessage(null);

    try {
      if (!autoTriggerPayloadPreview) {
        setAutoActionError('지금 실행 Payload를 구성할 수 없습니다.');
        return;
      }

      const result = await preprocessService.triggerFeatureAutoJobs(autoTriggerPayloadPreview);

      if (result.requested_job_count === 0 || result.results.length === 0) {
        setAutoActionError('실행 가능한 자동 생성 정책이 없습니다. 먼저 자동 생성 정책을 저장하세요.');
        return;
      }

      const firstResult = result.results[0];
      if (firstResult.status === 'ERROR') {
        setAutoActionError(firstResult.message || '수동 트리거 실행 중 오류가 발생했습니다.');
      } else {
        setAutoActionMessage(
          `${firstResult.status}: ${firstResult.dataset_label} (생성 ${firstResult.created_count}, 기존 ${firstResult.skipped_count}, 윈도우 ${firstResult.total_window_count})`,
        );
      }

      await loadFeatureAutoJobs();
      if (effectiveDatasetToken) {
        await loadFeaturePreview(effectivePolicyDatasetKey, effectiveDatasetToken);
      }
    } catch (error: unknown) {
      setAutoActionError(error instanceof Error ? error.message : '수동 트리거 실행에 실패했습니다.');
    } finally {
      setTriggeringAutoJob(false);
    }
  }, [
    autoTriggerPayloadPreview,
    effectiveDatasetToken,
    effectivePolicyDatasetKey,
    featureEnabled,
    loadFeatureAutoJobs,
    loadFeaturePreview,
  ]);

  useEffect(() => {
    if (!effectiveDatasetToken || !effectivePolicyDatasetKey) {
      setFeatureData(null);
      setFeatureError(null);
      setFeaturePreviewMetric(null);
      loadedFeatureDatasetTokenRef.current = '';
      return;
    }

    if (dataViewMode !== 'feature') {
      return;
    }

    if (loadedFeatureDatasetTokenRef.current === effectiveDatasetToken && featureData) {
      return;
    }

    void loadFeaturePreview(effectivePolicyDatasetKey, effectiveDatasetToken);
  }, [dataViewMode, effectiveDatasetToken, effectivePolicyDatasetKey, featureData, loadFeaturePreview]);

  const canSaveAutoPolicy = Boolean(autoPolicyPayloadPreview);

  const featureRowsForRender = useMemo(() => {
    if (!featureData) {
      return [] as FeaturePreviewRow[];
    }
    return featureData.featureRows.slice(0, FEATURE_RENDER_SAMPLE_LIMIT);
  }, [featureData]);

  const featureColumns = useMemo(() => {
    if (featureRowsForRender.length === 0) {
      return [] as string[];
    }

    const baseColumns = FEATURE_BASE_COLUMNS.filter((column) =>
      featureRowsForRender.some((row) => Object.hasOwn(row, column)),
    );

    const metricColumnSet: Record<string, Set<string>> = {
      MEAN: new Set<string>(),
      STD: new Set<string>(),
      MIN: new Set<string>(),
      MAX: new Set<string>(),
    };

    featureRowsForRender.forEach((row) => {
      const featureValues = row.feature_values;
      if (!isRecord(featureValues)) {
        return;
      }

      FEATURE_STAT_COLUMNS.forEach((statKey) => {
        const statValues = featureValues[statKey];
        if (!isRecord(statValues)) {
          return;
        }

        Object.keys(statValues).forEach((column) => {
          metricColumnSet[statKey].add(column);
        });
      });
    });

    const metricColumns: string[] = [];
    FEATURE_STAT_COLUMNS.forEach((statKey) => {
      Array.from(metricColumnSet[statKey])
        .sort((left, right) => left.localeCompare(right))
        .forEach((column) => {
          metricColumns.push(`${statKey}.${column}`);
        });
    });

    return [...baseColumns, ...metricColumns];
  }, [featureRowsForRender]);

  useEffect(() => {
    if (featureRowsForRender.length === 0 || featureRenderMeasureStartRef.current === null) {
      return;
    }

    const renderStartedAt = featureRenderMeasureStartRef.current;
    const frameId = window.requestAnimationFrame(() => {
      setFeaturePreviewMetric((previous) => {
        if (!previous) {
          return previous;
        }
        return {
          ...previous,
          renderMs: performance.now() - renderStartedAt,
          renderedRowCount: featureRowsForRender.length,
        };
      });
      featureRenderMeasureStartRef.current = null;
    });

    return () => {
      window.cancelAnimationFrame(frameId);
    };
  }, [featureRowsForRender.length, featureColumns.length]);

  // ── 선택 핸들러 ──

  const selectDataDetail = (typeCode: string, dtlCode: string) => {
    setSelectedTypeCode(typeCode);
    setSelectedDtlCode(dtlCode);
    setExpandedTypeCode(typeCode);
    const targetType = dataSources.find((item) => item.typeCode === typeCode);
    const targetDetail = targetType?.details.find((item) => item.dtlCode === dtlCode);
    const firstDataset = targetDetail?.datasets?.[0] ?? null;
    setSelectedDatasetKey(firstDataset?.datasetKey ?? '');
    setAutoActionError(null);
    setAutoActionMessage(null);
    syncedPolicyTokenRef.current = '';

    if (firstDataset) {
      const toggleKey = resolveEquipmentGroupToggleKeyForDataset(typeCode, dtlCode, firstDataset);
      ensureEquipmentGroupExpanded(toggleKey);
    }
  };

  const selectDataset = (typeCode: string, dtlCode: string, datasetKey: string, dataset?: DataSourceDataset) => {
    setSelectedTypeCode(typeCode);
    setSelectedDtlCode(dtlCode);
    setSelectedDatasetKey(datasetKey);
    setExpandedTypeCode(typeCode);
    setAutoActionError(null);
    setAutoActionMessage(null);
    syncedPolicyTokenRef.current = '';

    if (dataset) {
      const toggleKey = resolveEquipmentGroupToggleKeyForDataset(typeCode, dtlCode, dataset);
      ensureEquipmentGroupExpanded(toggleKey);
    }
  };

  const toggleDataSourceType = (type: DataSourceType, isExpanded: boolean) => {
    setExpandedTypeCode(isExpanded ? type.typeCode : null);
  };

  const toggleColumn = (column: string) => {
    setSelectedColumns((previousColumns) => {
      if (!previewData) {
        return previousColumns;
      }

      if (previousColumns.includes(column)) {
        return previousColumns.filter((item) => item !== column);
      }

      const nextColumns = [...previousColumns, column];
      return previewData.availableColumns.filter((item) => nextColumns.includes(item));
    });
  };

  const moveToAlgorithmSelection = () => {
    const normalizedDatasetKey = selectedDatasetKey.trim();
    if (normalizedDatasetKey) {
      persistOperationDatasetKey(normalizedDatasetKey);
    }
    const query = normalizedDatasetKey ? `?datasetKey=${encodeURIComponent(normalizedDatasetKey)}` : '';
    navigate(`/operation/algorithm${query}`, {
      state: {
        dataset_key: normalizedDatasetKey,
        datasetKey: normalizedDatasetKey,
        selectedDataset: normalizedDatasetKey,
      },
    });
  };

  // ── 렌더링 ──

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h4">AI 데이터 전처리</Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
          자동 feature 생성 정책과 실행 상태를 확인하고, 관리자용 수동 트리거로 테스트할 수 있습니다.
        </Typography>
      </Box>

      <Alert
        severity="info"
        action={
          <Button size="small" variant="outlined" onClick={moveToAlgorithmSelection}>
            다음 단계 이동
          </Button>
        }
      >
        전처리 단계에서는 알고리즘 선택이 필요하지 않습니다. Feature 생성 후 알고리즘 선택 단계로 이동할 수 있습니다.
      </Alert>

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: {
            xs: '1fr',
            lg: '280px minmax(0, 1fr) 320px',
          },
          alignItems: 'start',
        }}
      >
        {/* ── 좌측 트리 패널 ── */}
        <Card variant="outlined">
          <CardContent sx={{ p: 1.25 }}>
            <Typography variant="subtitle1" fontWeight={700} sx={{ px: 1, pb: 0.8 }}>
              데이터 소스
            </Typography>
            <Divider sx={{ mb: 1 }} />

            {loadingSources && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
                <CircularProgress size={28} />
              </Box>
            )}

            {!loadingSources && sourceError && <Alert severity="error">{sourceError}</Alert>}

            {!loadingSources && !sourceError && dataSources.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ px: 1 }}>
                활성화된 데이터 소스가 없습니다.
              </Typography>
            )}

            {!loadingSources && !sourceError && dataSources.length > 0 && (
              <Stack spacing={1}>
                {dataSources.map((type) => (
                  <Accordion
                    key={type.typeCode}
                    disableGutters
                    expanded={expandedTypeCode === type.typeCode}
                    onChange={(_event, isExpanded) => toggleDataSourceType(type, isExpanded)}
                    sx={{
                      border: '1px solid #d6deea',
                      borderRadius: 2,
                      overflow: 'hidden',
                      '&:before': { display: 'none' },
                    }}
                  >
                    <AccordionSummary
                      expandIcon={<ExpandMoreIcon />}
                      sx={{
                        minHeight: 46,
                        '& .MuiAccordionSummary-content': { my: 1 },
                        ...(selectedTypeCode === type.typeCode && {
                          backgroundColor: '#edf4ff',
                          color: '#11489f',
                          borderRadius: '8px 8px 0 0',
                        }),
                      }}
                    >
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ width: '100%' }}>
                        <Chip
                          label={type.typeCode}
                          size="small"
                          color={selectedTypeCode === type.typeCode ? 'primary' : 'default'}
                        />
                        <Typography variant="subtitle2" fontWeight={700}>
                          {type.typeName}
                        </Typography>
                      </Stack>
                    </AccordionSummary>

                    <AccordionDetails sx={{ py: 0.5 }}>
                      <List disablePadding>
                        {type.details.map((detail) => {
                          const detailDatasets = detail.datasets ?? [];
                          const hasDatasets = detailDatasets.length > 0;
                          const isDetailSelected = selectedTypeCode === type.typeCode && selectedDtlCode === detail.dtlCode;
                          const purposeGroups = hasDatasets ? groupDatasetsByPurposeAndEquipment(detailDatasets) : [];

                          return (
                            <Box key={`${type.typeCode}-${detail.dtlCode}`} sx={{ mb: 0.5 }}>
                              <ListItemButton
                                selected={isDetailSelected && !hasDatasets}
                                onClick={() => selectDataDetail(type.typeCode, detail.dtlCode)}
                                sx={{
                                  borderRadius: 1.5,
                                  '&.Mui-selected': {
                                    backgroundColor: '#e7efff',
                                    color: '#11489f',
                                    borderRadius: 1.5,
                                  },
                                }}
                              >
                                <ListItemText
                                  primary={detail.dtlName}
                                  secondary={detail.dtlCode}
                                  primaryTypographyProps={{ fontSize: 13, fontWeight: 700 }}
                                  secondaryTypographyProps={{ fontSize: 11 }}
                                />
                              </ListItemButton>

                              {/* ── PURPOSE → EQUIPMENT_GROUP → DATASET 계층 트리 ── */}
                              {hasDatasets && (
                                <List disablePadding sx={{ pl: 1, mt: 0.3 }}>
                                  {purposeGroups.map((purposeGroup) => (
                                    <Box key={purposeGroup.purposeKey} sx={{ mb: 0.8 }}>
                                      {/* PURPOSE 헤더 */}
                                      <Box sx={{ px: 1, py: 0.4 }}>
                                        <Chip
                                          size="small"
                                          color={purposeGroup.chipColor}
                                          label={purposeGroup.purposeLabel}
                                          sx={{ height: 21, fontSize: 11, fontWeight: 700 }}
                                        />
                                      </Box>

                                      {/* EQUIPMENT_GROUP 리스트 */}
                                      {purposeGroup.equipmentGroups.map((eqGroup) => {
                                        const toggleKey = buildEquipmentGroupToggleKey(
                                          type.typeCode,
                                          detail.dtlCode,
                                          purposeGroup.purposeKey,
                                          eqGroup.groupKey,
                                        );
                                        const isGroupExpanded = expandedEquipmentGroups.has(toggleKey);

                                        return (
                                          <Box key={eqGroup.groupKey}>
                                            {/* 설비그룹 토글 버튼 */}
                                            <ListItemButton
                                              onClick={() => toggleEquipmentGroup(toggleKey)}
                                              sx={{
                                                borderRadius: 1.5,
                                                py: 0.3,
                                                minHeight: 32,
                                              }}
                                            >
                                              <ListItemIcon sx={{ minWidth: 28 }}>
                                                {isGroupExpanded ? (
                                                  <FolderOpenIcon sx={{ fontSize: 18, color: '#5a7fb5' }} />
                                                ) : (
                                                  <FolderIcon sx={{ fontSize: 18, color: '#8a9bb5' }} />
                                                )}
                                              </ListItemIcon>
                                              <ListItemText
                                                primary={
                                                  <Stack direction="row" spacing={0.5} alignItems="center">
                                                    <Typography component="span" fontSize={12} fontWeight={700}>
                                                      {eqGroup.groupName}
                                                    </Typography>
                                                    <Typography component="span" fontSize={11} color="text.secondary">
                                                      ({eqGroup.datasets.length})
                                                    </Typography>
                                                  </Stack>
                                                }
                                              />
                                              {isGroupExpanded ? (
                                                <ExpandLess sx={{ fontSize: 16 }} />
                                              ) : (
                                                <ExpandMore sx={{ fontSize: 16 }} />
                                              )}
                                            </ListItemButton>

                                            {/* 개별 DATASET 리스트 */}
                                            <Collapse in={isGroupExpanded} timeout="auto" unmountOnExit>
                                              <List disablePadding sx={{ pl: 2 }}>
                                                {eqGroup.datasets.map((dataset) => (
                                                  <ListItemButton
                                                    key={`${type.typeCode}-${detail.dtlCode}-${dataset.datasetKey}`}
                                                    selected={
                                                      isDetailSelected && selectedDatasetKey === dataset.datasetKey
                                                    }
                                                    onClick={() =>
                                                      selectDataset(type.typeCode, detail.dtlCode, dataset.datasetKey, dataset)
                                                    }
                                                    sx={{
                                                      borderRadius: 1.5,
                                                      mb: 0.3,
                                                      minHeight: 34,
                                                      '&.Mui-selected': {
                                                        backgroundColor: '#e7efff',
                                                        color: '#11489f',
                                                        borderRadius: 1.5,
                                                      },
                                                    }}
                                                  >
                                                    <ListItemText
                                                      primary={
                                                        <Typography component="span" fontSize={12} fontWeight={600}>
                                                          {dataset.displayName ?? dataset.datasetName ?? dataset.datasetKey}
                                                        </Typography>
                                                      }
                                                      secondary={dataset.datasetName ?? dataset.datasetKey}
                                                      secondaryTypographyProps={{ fontSize: 10.5 }}
                                                    />
                                                  </ListItemButton>
                                                ))}
                                              </List>
                                            </Collapse>
                                          </Box>
                                        );
                                      })}
                                    </Box>
                                  ))}
                                </List>
                              )}
                            </Box>
                          );
                        })}
                      </List>
                    </AccordionDetails>
                  </Accordion>
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>

        {/* ── 중앙 데이터 미리보기 패널 ── */}
        <Card variant="outlined">
          <CardContent>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', sm: 'center' }}>
              <Typography variant="subtitle1" fontWeight={700}>
                데이터 미리보기
              </Typography>
              {selectedDetail && (
                <>
                  <Chip size="small" label={`유형: ${selectedDetail.type.typeCode}`} />
                  <Chip size="small" label={`상세: ${selectedDetail.detail.dtlCode}`} />
                </>
              )}
              {effectiveDatasetLabel && <Chip size="small" label={`dataset: ${effectiveDatasetLabel}`} />}
              <Chip
                size="small"
                color={featureEnabled ? 'success' : 'warning'}
                label={`Feature ${featureEnabled ? '활성' : '비활성'}`}
              />
              <Chip
                size="small"
                color={getDatasetPurposeChipColor(activeDataset)}
                label={`용도: ${buildDatasetPurposeLabel(activeDataset)}`}
              />
              {activeLabelField && <Chip size="small" label={`label: ${activeLabelField}`} />}
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.8 }}>
              {featureEnabled
                ? `RAW 최대 ${RAW_PREVIEW_LIMIT}건, FEATURE 조회 최대 ${FEATURE_PREVIEW_LIMIT}건, 렌더 샘플 최대 ${FEATURE_RENDER_SAMPLE_LIMIT}건`
                : `RAW 최대 ${RAW_PREVIEW_LIMIT}건`}
            </Typography>
            {dataViewMode === 'raw' && rawPreviewMetric && (
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.6, display: 'block' }}>
                RAW 응답 {formatBytes(rawPreviewMetric.responseBytes)} / API {formatDuration(rawPreviewMetric.requestMs)} /
                렌더 {formatDuration(rawPreviewMetric.renderMs)} / {rawPreviewMetric.renderedRowCount}건
              </Typography>
            )}
            {featureEnabled && dataViewMode === 'feature' && featurePreviewMetric && (
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.6, display: 'block' }}>
                FEATURE 응답 {formatBytes(featurePreviewMetric.responseBytes)} / API{' '}
                {formatDuration(featurePreviewMetric.requestMs)} / 렌더 {formatDuration(featurePreviewMetric.renderMs)} /{' '}
                {featurePreviewMetric.renderedRowCount}건 (조회 {featurePreviewMetric.rowCount}건)
              </Typography>
            )}

            {isFeatureSource && (
              <Alert severity="success" sx={{ mt: 1.4, whiteSpace: 'pre-line' }}>
                {FEATURE_SOURCE_GUIDE_MESSAGE}
              </Alert>
            )}

            {isSupervisedSource && (
              <Alert severity="warning" sx={{ mt: 1.4, whiteSpace: 'pre-line' }}>
                {SUPERVISED_DATASET_GUIDE_MESSAGE}
                {activeLabelField ? `\n현재 label 필드: ${activeLabelField}` : ''}
              </Alert>
            )}

            <Box
              sx={{
                mt: 1.2,
                display: 'grid',
                gap: 1,
                gridTemplateColumns: {
                  xs: '1fr',
                  md: 'repeat(3, minmax(0, 1fr))',
                },
              }}
            >
              <TextField
                label="PRDTIME 시작(KST)"
                type="date"
                value={rawPreviewFromDate}
                onChange={(event) => setRawPreviewFromDate(event.target.value)}
                InputLabelProps={{ shrink: true }}
                fullWidth
              />
              <TextField
                label="PRDTIME 종료(KST)"
                type="date"
                value={rawPreviewToDate}
                onChange={(event) => setRawPreviewToDate(event.target.value)}
                InputLabelProps={{ shrink: true }}
                fullWidth
              />
              <TextField
                label="MCCODE"
                placeholder="예: DEMO-MC-001"
                value={rawPreviewEquipmentId}
                onChange={(event) => setRawPreviewEquipmentId(event.target.value)}
                fullWidth
              />
            </Box>

            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1.5 }}>
              <ToggleButtonGroup
                exclusive
                size="small"
                value={dataViewMode}
                onChange={(_event, value: DataViewMode | null) => {
                  if (value === 'feature' && !featureEnabled) {
                    return;
                  }
                  if (value) {
                    setDataViewMode(value);
                  }
                }}
              >
                <ToggleButton value="raw">원본 데이터</ToggleButton>
                {featureEnabled && <ToggleButton value="feature">Feature 데이터</ToggleButton>}
              </ToggleButtonGroup>
            </Box>

            {!featureEnabled && (
              <Alert severity="info" sx={{ mt: 1.5, whiteSpace: 'pre-line' }}>
                {FEATURE_DISABLED_GUIDE_MESSAGE}
              </Alert>
            )}

            {featureTargetCount > 1 && (
              <Alert severity="info" sx={{ mt: 1.5 }}>
                현재 detail에 여러 dataset이 있습니다. 좌측 트리 또는 오른쪽 패널에서 비지도/운영용과 라벨링/지도학습용
                dataset을 구분해서 선택하세요.
              </Alert>
            )}

            <Divider sx={{ my: 1.5 }} />

            {dataViewMode === 'raw' && loadingPreview && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 7 }}>
                <CircularProgress size={30} />
              </Box>
            )}

            {dataViewMode === 'raw' && !loadingPreview && previewError && <Alert severity="error">{previewError}</Alert>}

            {dataViewMode === 'raw' && !loadingPreview && !previewError && previewData && previewData.rawRows.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                {hasSourceSelection ? '선택한 소스 조건에 해당하는 데이터가 없습니다.' : '표시할 원시 데이터가 없습니다.'}
              </Typography>
            )}

            {dataViewMode === 'raw' &&
              !loadingPreview &&
              !previewError &&
              previewData &&
              previewData.rawRows.length > 0 &&
              visibleColumns.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  오른쪽 패널에서 컬럼을 선택하면 테이블이 표시됩니다.
                </Typography>
              )}

            {dataViewMode === 'raw' &&
              !loadingPreview &&
              !previewError &&
              previewData &&
              previewData.rawRows.length > 0 &&
              visibleColumns.length > 0 && (
                <TableContainer sx={{ maxHeight: 560, border: '1px solid #d6deea', borderRadius: 2 }}>
                  <Table stickyHeader size="small">
                    <TableHead>
                      <TableRow>
                        {visibleColumns.map((column) => (
                          <TableCell key={column} sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                            {column}
                          </TableCell>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {previewData.rawRows.map((row, rowIndex) => (
                        <TableRow key={`${selectedTypeCode}-${selectedDtlCode}-${selectedDatasetKey}-${rowIndex}`} hover>
                          {visibleColumns.map((column) => (
                            <TableCell key={`${rowIndex}-${column}`} sx={{ whiteSpace: 'nowrap' }}>
                              {formatCellValue(row[column])}
                            </TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}

            {featureEnabled && dataViewMode === 'feature' && loadingFeature && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 7 }}>
                <CircularProgress size={30} />
              </Box>
            )}

            {featureEnabled && dataViewMode === 'feature' && !loadingFeature && featureError && <Alert severity="error">{featureError}</Alert>}

            {featureEnabled && dataViewMode === 'feature' && !loadingFeature && !featureError && !effectivePolicyDatasetKey && (
              <Typography variant="body2" color="text.secondary">
                원본 데이터에서 dataset_key를 찾을 수 없어 feature 조회가 불가능합니다.
              </Typography>
            )}

            {featureEnabled &&
              dataViewMode === 'feature' &&
              !loadingFeature &&
              !featureError &&
              featureData &&
              featureData.featureRows.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  생성된 feature 데이터가 없습니다.
                </Typography>
              )}

            {featureEnabled &&
              dataViewMode === 'feature' &&
              !loadingFeature &&
              !featureError &&
              featureData &&
              featureData.featureRows.length > 0 &&
              featureColumns.length > 0 && (
                <Stack spacing={1}>
                  {featureData.featureRows.length > featureRowsForRender.length && (
                    <Alert severity="info">
                      FEATURE 컬럼/테이블은 샘플 {featureRowsForRender.length}건 기준으로 렌더링합니다. (조회{' '}
                      {featureData.featureRows.length}건)
                    </Alert>
                  )}
                  <TableContainer sx={{ maxHeight: 560, border: '1px solid #d6deea', borderRadius: 2 }}>
                    <Table stickyHeader size="small">
                      <TableHead>
                        <TableRow>
                          {featureColumns.map((column) => (
                            <TableCell key={column} sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                              {column}
                            </TableCell>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {featureRowsForRender.map((row, rowIndex) => (
                          <TableRow
                            key={`${row.window_start ?? rowIndex}-${row.window_end ?? rowIndex}-${rowIndex}`}
                            hover
                          >
                            {featureColumns.map((column) => (
                              <TableCell key={`${rowIndex}-${column}`} sx={{ whiteSpace: 'nowrap' }}>
                                {formatCellValue(extractFeatureMetricValue(row, column))}
                              </TableCell>
                            ))}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </Stack>
              )}
          </CardContent>
        </Card>

        {/* ── 우측 컬럼 선택 / 정책 패널 ── */}
        <Stack spacing={2}>
          <Card variant="outlined">
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="subtitle1" fontWeight={700}>
                  컬럼 선택
                </Typography>
                {previewData && (
                  <Typography variant="caption" color="text.secondary">
                    {visibleColumns.length} / {previewData.availableColumns.length}
                  </Typography>
                )}
              </Stack>

              <Divider sx={{ my: 1.2 }} />

              {!previewData && (
                <Typography variant="body2" color="text.secondary">
                  원시 데이터 미리보기가 로드되면 표시할 컬럼을 선택할 수 있습니다.
                </Typography>
              )}

              {previewData && (
                <Stack spacing={1.2}>
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="outlined" onClick={() => setSelectedColumns(previewData.availableColumns)}>
                      전체 선택
                    </Button>
                    <Button size="small" variant="text" onClick={() => setSelectedColumns([])}>
                      초기화
                    </Button>
                  </Stack>

                  <Box
                    sx={{
                      maxHeight: 400,
                      overflowY: 'auto',
                      border: '1px solid #d6deea',
                      borderRadius: 2,
                      p: 1,
                    }}
                  >
                    <FormGroup>
                      {previewData.availableColumns.map((column) => (
                        <FormControlLabel
                          key={column}
                          control={<Checkbox size="small" checked={selectedColumns.includes(column)} onChange={() => toggleColumn(column)} />}
                          label={column}
                        />
                      ))}
                    </FormGroup>
                  </Box>

                  <Divider sx={{ my: 0.6 }} />

                  <Typography variant="subtitle2" fontWeight={700}>
                    source dataset
                  </Typography>
                  <TextField
                    select
                    label="source dataset"
                    size="small"
                    value={selectedDatasetKey}
                    onChange={(event) => setSelectedDatasetKey(event.target.value)}
                    disabled={sourceDatasetOptions.length === 0}
                  >
                    {sourceDatasetOptions.map((option) => {
                      return (
                        <MenuItem key={option.dataset.datasetKey} value={option.dataset.datasetKey}>
                          {option.label} / {buildDatasetPurposeLabel(option.dataset)}
                        </MenuItem>
                      );
                    })}
                  </TextField>

                  <Alert
                    severity={isSupervisedSource ? 'warning' : 'info'}
                    sx={{ whiteSpace: 'pre-line' }}
                  >
                    {isSupervisedSource
                      ? SUPERVISED_DATASET_GUIDE_MESSAGE
                      : FEATURE_SOURCE_GUIDE_MESSAGE}
                  </Alert>

                  {featureEnabled ? (
                    <>
                      <Typography variant="subtitle2" fontWeight={700}>
                        Feature 자동 생성 정책
                      </Typography>

                      {loadingAutoJobs && (
                        <Box sx={{ display: 'flex', justifyContent: 'center', py: 1.2 }}>
                          <CircularProgress size={20} />
                        </Box>
                      )}

                      {!loadingAutoJobs && autoJobError && <Alert severity="error">{autoJobError}</Alert>}

                      {!loadingAutoJobs && !autoJobError && autoJobData && (
                        <Alert severity={autoJobData.scheduler_enabled ? 'info' : 'warning'}>
                          스케줄러: {autoJobData.scheduler_enabled ? '활성' : '비활성'} / 주기 약{' '}
                          {Math.max(1, Math.floor(autoJobData.scheduler_fixed_delay_ms / 1000))}초
                        </Alert>
                      )}

                      <Typography variant="caption" color="text.secondary">
                        숫자형 컬럼 {previewData.numericColumns.length}개 중 선택 {selectedFeatureColumns.length}개
                      </Typography>

                      <TextField
                        label="window size"
                        type="number"
                        size="small"
                        value={windowSize}
                        onChange={(event) => {
                          const parsed = Number.parseInt(event.target.value, 10);
                          setWindowSize(Number.isNaN(parsed) ? 0 : parsed);
                        }}
                        inputProps={{ min: 1 }}
                      />

                      <Box sx={{ border: '1px solid #d6deea', borderRadius: 2, p: 1.2, bgcolor: '#f8fbff' }}>
                        <Typography variant="caption" sx={{ display: 'block', fontWeight: 700, mb: 0.4 }}>
                          Payload Preview
                        </Typography>
                        <Typography variant="caption" sx={{ display: 'block', mb: 0.3 }}>
                          자동 정책 저장
                        </Typography>
                        {autoPolicyPayloadPreview ? (
                          <Box
                            component="pre"
                            sx={{
                              m: 0,
                              p: 1,
                              borderRadius: 1,
                              border: '1px solid #d6deea',
                              bgcolor: '#ffffff',
                              fontSize: 11,
                              lineHeight: 1.35,
                              overflowX: 'auto',
                            }}
                          >
                            {JSON.stringify(autoPolicyPayloadPreview, null, 2)}
                          </Box>
                        ) : (
                          <Typography variant="caption" color="text.secondary">
                            필수 조건(dataset_key/컬럼/window size)이 충족되면 표시됩니다.
                          </Typography>
                        )}

                        <Typography variant="caption" sx={{ display: 'block', mt: 1, mb: 0.3 }}>
                          지금 실행(관리자)
                        </Typography>
                        {autoTriggerPayloadPreview ? (
                          <Box
                            component="pre"
                            sx={{
                              m: 0,
                              p: 1,
                              borderRadius: 1,
                              border: '1px solid #d6deea',
                              bgcolor: '#ffffff',
                              fontSize: 11,
                              lineHeight: 1.35,
                              overflowX: 'auto',
                            }}
                          >
                            {JSON.stringify(autoTriggerPayloadPreview, null, 2)}
                          </Box>
                        ) : (
                          <Typography variant="caption" color="text.secondary">
                            dataset_key를 결정하면 표시됩니다.
                          </Typography>
                        )}
                      </Box>

                      <Stack direction="row" spacing={1}>
                        <Button
                          variant="contained"
                          onClick={() => void saveAutoPolicy()}
                          disabled={savingAutoPolicy || !canSaveAutoPolicy}
                        >
                          {savingAutoPolicy ? '저장 중...' : '자동 정책 저장'}
                        </Button>
                        <Button
                          variant="outlined"
                          onClick={() => void triggerAutoGenerationNow()}
                          disabled={triggeringAutoJob || !autoTriggerPayloadPreview}
                        >
                          {triggeringAutoJob ? '실행 중...' : '지금 실행(관리자)'}
                        </Button>
                      </Stack>

                      {selectedAutoJob && (
                        <Box sx={{ border: '1px solid #d6deea', borderRadius: 2, p: 1.2, bgcolor: '#f8fbff' }}>
                          <Typography variant="caption" sx={{ display: 'block' }}>
                            target feature dataset: {selectedAutoJob.target_collection}
                          </Typography>
                          <Typography variant="caption" sx={{ display: 'block' }}>
                            마지막 상태: {selectedAutoJob.last_run_status} / 대기 raw: {selectedAutoJob.pending_raw_count}건
                          </Typography>
                          <Typography variant="caption" sx={{ display: 'block' }}>
                            마지막 실행: {selectedAutoJob.last_run_finished_at ?? '-'}
                          </Typography>
                          <Typography variant="caption" sx={{ display: 'block' }}>
                            마지막 처리 시각: {selectedAutoJob.last_processed_timestamp ?? '-'}
                          </Typography>
                          <Typography variant="caption" sx={{ display: 'block' }}>
                            마지막 윈도우 종료: {selectedAutoJob.last_window_end ?? '-'}
                          </Typography>
                          <Typography variant="caption" sx={{ display: 'block' }}>
                            최근 생성/중복: {selectedAutoJob.last_created_count}/{selectedAutoJob.last_skipped_count} (총{' '}
                            {selectedAutoJob.last_total_window_count} 윈도우)
                          </Typography>
                        </Box>
                      )}

                      {!selectedAutoJob && (
                        <Alert severity="warning">
                          선택 dataset에 대한 자동 정책이 없습니다. 컬럼/윈도우를 선택한 뒤 자동 정책을 저장하세요.
                        </Alert>
                      )}

                      {autoActionMessage && <Alert severity="success">{autoActionMessage}</Alert>}
                      {autoActionError && <Alert severity="error">{autoActionError}</Alert>}
                    </>
                  ) : (
                    <Stack spacing={1}>
                      <Alert severity="warning" sx={{ whiteSpace: 'pre-line' }}>
                        {FEATURE_DISABLED_GUIDE_MESSAGE}
                        {'\n'}
                        preview 컬럼(예: label, label_name, label_reason, ANAL_STAT, OPSTAT)은 조회용으로만 사용합니다.
                      </Alert>
                      <Box sx={{ border: '1px solid #f0d59a', borderRadius: 2, p: 1.2, bgcolor: '#fffaf0' }}>
                        <Typography variant="caption" sx={{ display: 'block', fontWeight: 700, mb: 0.4 }}>
                          라벨링 데이터셋 처리 기준
                        </Typography>
                        <Typography variant="caption" sx={{ display: 'block' }}>
                          ANAL_STAT=0: 정상 후보 / ANAL_STAT=2,3: 이상 후보 / ANAL_STAT=1: 측정중 또는 제외 후보
                        </Typography>
                        <Typography variant="caption" sx={{ display: 'block' }}>
                          OPSTAT은 라벨보다 공정 구간 필터로 사용하고, HEATER_STAT/OPALARM은 확정 전까지 라벨 기준에서 제외합니다.
                        </Typography>
                      </Box>
                    </Stack>
                  )}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Stack>
      </Box>
    </Stack>
  );
}

// ── 유틸리티 함수 (모듈 레벨) ──

function findFirstDatasetNode(dataSources: DataSourceType[]): {
  typeCode: string;
  dtlCode: string;
  dataset: DataSourceDataset;
} | null {
  for (const type of dataSources) {
    for (const detail of type.details) {
      const datasets = detail.datasets ?? [];
      if (datasets.length > 0) {
        return {
          typeCode: type.typeCode,
          dtlCode: detail.dtlCode,
          dataset: datasets[0],
        };
      }
    }
  }
  return null;
}

function findDatasetNodeByKey(
    dataSources: DataSourceType[],
    datasetKey: string | undefined,
  ): {
    typeCode: string;
    dtlCode: string;
    dataset: DataSourceDataset;
  } | null {
    if (!datasetKey) {
      return null;
    }

    for (const type of dataSources) {
      for (const detail of type.details) {
        const datasets = detail.datasets ?? [];
        const matchedDataset = datasets.find((dataset) => dataset.datasetKey === datasetKey);

        if (matchedDataset) {
          return {
            typeCode: type.typeCode,
            dtlCode: detail.dtlCode,
            dataset: matchedDataset,
          };
        }
      }
    }

    return null;
  }

function normalizeDatasetKeyString(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }

  const trimmed = value.trim().toLowerCase();
  if (trimmed.length === 0 || trimmed === '{}' || trimmed === '[]') {
    return null;
  }

  const normalized = trimmed
    .replace(/[^a-z0-9_]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '');

  return normalized.length > 0 ? normalized : null;
}

function normalizeOptionalText(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function buildDatasetOptionLabel(dataset: DataSourceDataset): string {
  const datasetName = normalizeOptionalText(dataset.datasetName);
  const displayName = normalizeOptionalText(dataset.displayName);

  if (displayName && datasetName) {
    return `${displayName} (${datasetName})`;
  }
  if (displayName) {
    return displayName;
  }
  if (datasetName) {
    return datasetName;
  }
  return dataset.datasetKey;
}

function buildDatasetStatusLabel(dataset: DataSourceDataset | null): string | null {
  if (!dataset) {
    return null;
  }
  const datasetName = normalizeOptionalText(dataset.datasetName);
  const displayName = normalizeOptionalText(dataset.displayName);

  if (datasetName && displayName) {
    return `${datasetName} (${displayName})`;
  }
  if (datasetName) {
    return datasetName;
  }
  if (displayName) {
    return displayName;
  }
  return normalizeOptionalText(dataset.datasetKey);
}
