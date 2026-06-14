import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  MenuItem,
  Stack,
  Switch,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
  defaultAlgorithmDatasetKey,
  randomForestDatasetKey,
} from '../constants/algorithmDatasetPolicy';
import { algorithmSelectionService } from '../services/algorithmSelectionService';
import { algorithmParamService } from '../services/algorithmParamService';
import { dataExplorationService } from '../services/dataExplorationService';
import { modelTrainService } from '../services/modelTrainService';
import { useAuth } from '../store/AuthContext';
import { useSelectedAlgorithm } from '../store/SelectedAlgorithmContext';
import type { AlgorithmParamItem } from '../types/algorithmParam';
import type { AlgorithmActiveSelectionData } from '../types/algorithmSelection';
import type {
  AnomalyResultRow,
  FeatureDataset,
  ModelTrainAutoPolicyStatus,
  ModelTrainNavigationState,
  ParamFormValueMap,
  ParamValidationErrorMap,
} from '../types/modelTrain';
import type { OperationDatasetOption } from '../utils/operationDataset';
import {
  extractDatasetKeyFromRouteState,
  filterOperationDatasetOptions,
  persistOperationDatasetKey,
  readPersistedOperationDatasetKey,
  resolveOperationDatasetKeyWithFallback,
  resolveOperationDatasetLabel,
} from '../utils/operationDataset';

const DEFAULT_WINDOW_SIZE = 100;
const DEFAULT_SCHEDULER_INTERVAL_SEC = 600;
const DEFAULT_MIN_NEW_FEATURE_COUNT = 50;
const DEFAULT_MIN_TOTAL_FEATURE_COUNT = 200;
const RESULT_LIMIT = 1000;
const DEFAULT_DATASET_NAME = 'Demo HMI Dataset';

const DEFAULT_OPERATION_DATASET_KEY = defaultAlgorithmDatasetKey();
const SUPERVISED_DATASET_KEY = randomForestDatasetKey();

const UNSUPERVISED_ALGO_CODES = ['ISOLATION_FOREST', 'AUTOENCODER'];
const SUPERVISED_ALGO_CODES = ['RANDOM_FOREST'];

const RANDOM_FOREST_PARAM_CODES = new Set([
  'N_ESTIMATORS',
  'MAX_DEPTH',
  'MIN_SAMPLES_SPLIT',
  'MIN_SAMPLES_LEAF',
  'MAX_FEATURES',
  'CLASS_WEIGHT',
  'TRAIN_VALID_RATIO',
  'HYPERPARAM_OPT_METHOD',
  'RETRAIN_CYCLE',
  'SEED',
]);

type LearningMode = 'UNSUPERVISED' | 'SUPERVISED';

function toStringValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value);
}

function normalizeDataType(dataType: string): string {
  return dataType.trim().toLowerCase();
}

function isNumericType(dataType: string): boolean {
  const normalized = normalizeDataType(dataType);
  return ['int', 'integer', 'long', 'short', 'float', 'double', 'decimal', 'number'].includes(normalized);
}

function isIntegerType(dataType: string): boolean {
  const normalized = normalizeDataType(dataType);
  return ['int', 'integer', 'long', 'short'].includes(normalized);
}

function toNumberOrNull(value: string | number | null): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  const text = String(value).trim();
  if (!text) {
    return null;
  }
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : null;
}

function normalizeColumns(columns: string[] | undefined): string[] {
  if (!columns || columns.length === 0) {
    return [];
  }

  return Array.from(
    new Set(
      columns
        .filter((column) => typeof column === 'string')
        .map((column) => column.trim())
        .filter((column) => column.length > 0),
    ),
  ).sort((left, right) => left.localeCompare(right));
}

function normalizeDatasetKeyString(datasetKey: unknown): string | null {
  if (typeof datasetKey !== 'string') {
    return null;
  }

  const trimmed = datasetKey.trim().toLowerCase();
  if (!trimmed || trimmed === '{}' || trimmed === '[]') {
    return null;
  }

  const normalized = trimmed
    .replace(/[^a-z0-9_]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '');
  return normalized.length > 0 ? normalized : null;
}

function buildDatasetIdentity(datasetKey: string | null | undefined): string {
  const normalized = normalizeDatasetKeyString(datasetKey);
  return normalized ?? DEFAULT_OPERATION_DATASET_KEY;
}

function normalizeOptionalText(value: string | null | undefined): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function normalizeAlgoCode(algoCode: string | null | undefined): string | null {
  const normalized = normalizeOptionalText(algoCode);
  return normalized ? normalized.toUpperCase() : null;
}

function defaultAlgoName(algoCode: string): string {
  if (algoCode === 'ISOLATION_FOREST') {
    return 'Isolation Forest';
  }
  if (algoCode === 'AUTOENCODER') {
    return 'AutoEncoder';
  }
  if (algoCode === 'RANDOM_FOREST') {
    return 'Random Forest';
  }
  return algoCode;
}

function resolveModeByAlgoCode(algoCode: string | null | undefined): LearningMode | null {
  const normalizedAlgoCode = normalizeAlgoCode(algoCode);
  if (!normalizedAlgoCode) {
    return null;
  }
  if (SUPERVISED_ALGO_CODES.includes(normalizedAlgoCode)) {
    return 'SUPERVISED';
  }
  if (UNSUPERVISED_ALGO_CODES.includes(normalizedAlgoCode)) {
    return 'UNSUPERVISED';
  }
  return null;
}

function parseUpdatedAtTimestamp(updatedAt: string | null | undefined): number {
  const normalized = normalizeOptionalText(updatedAt);
  if (!normalized) {
    return 0;
  }
  const timestamp = Date.parse(normalized);
  return Number.isFinite(timestamp) ? timestamp : 0;
}

function resolveInitialLearningMode(
  navigationState: ModelTrainNavigationState,
  selectedAlgorithmCode: string | null | undefined,
  unsupervisedActiveSelection: AlgorithmActiveSelectionData | null,
  supervisedActiveSelection: AlgorithmActiveSelectionData | null,
): LearningMode {
  const modeByNavigationAlgo = resolveModeByAlgoCode(navigationState.algoCd);
  if (modeByNavigationAlgo) {
    return modeByNavigationAlgo;
  }

  const navigationDatasetIdentity = normalizeDatasetKeyString(navigationState.dataset_key);
  if (navigationDatasetIdentity) {
    if (navigationDatasetIdentity === SUPERVISED_DATASET_KEY) {
      return 'SUPERVISED';
    }
    return 'UNSUPERVISED';
  }

  const modeBySelectedAlgorithm = resolveModeByAlgoCode(selectedAlgorithmCode);
  if (modeBySelectedAlgorithm) {
    return modeBySelectedAlgorithm;
  }

  const unsupervisedAlgoCode = normalizeAlgoCode(unsupervisedActiveSelection?.active_algo_code);
  const supervisedAlgoCode = normalizeAlgoCode(supervisedActiveSelection?.active_algo_code);

  const hasUnsupervisedActive = !!unsupervisedAlgoCode && UNSUPERVISED_ALGO_CODES.includes(unsupervisedAlgoCode);
  const hasSupervisedActive = !!supervisedAlgoCode && SUPERVISED_ALGO_CODES.includes(supervisedAlgoCode);

  if (hasSupervisedActive && !hasUnsupervisedActive) {
    return 'SUPERVISED';
  }
  if (hasUnsupervisedActive && !hasSupervisedActive) {
    return 'UNSUPERVISED';
  }
  if (hasSupervisedActive && hasUnsupervisedActive) {
    const supervisedUpdatedAt = parseUpdatedAtTimestamp(supervisedActiveSelection?.updated_at);
    const unsupervisedUpdatedAt = parseUpdatedAtTimestamp(unsupervisedActiveSelection?.updated_at);
    return supervisedUpdatedAt > unsupervisedUpdatedAt ? 'SUPERVISED' : 'UNSUPERVISED';
  }

  return 'UNSUPERVISED';
}

function resolveActiveSelectionWarning(
  activeSelection: AlgorithmActiveSelectionData | null,
  requestedDatasetKey: string,
): string | null {
  if (!activeSelection) {
    return null;
  }

  const activePolicyId = normalizeOptionalText(activeSelection.active_policy_id) ?? '-';
  const activeAlgoCode = normalizeOptionalText(activeSelection.active_algo_code);
  if (!activeAlgoCode) {
    return `Active policy(${activePolicyId}) was found, but active_algo_code is missing.`;
  }

  const activeAlgoName = normalizeOptionalText(activeSelection.active_algo_name);
  if (!activeAlgoName) {
    return `Active policy(${activePolicyId}) has no algorithm name. Showing code(${activeAlgoCode}) instead.`;
  }

  const requestedDataset = buildDatasetIdentity(requestedDatasetKey);
  const activeDataset = buildDatasetIdentity(activeSelection.dataset_key);
  if (requestedDataset !== activeDataset) {
    return `요청 dataset(${requestedDataset})과 활성 dataset(${activeDataset})이 달라 활성 dataset 기준으로 표시합니다.`;
  }

  return null;
}

function resolveDatasetNameFromDatasetKey(datasetKey: string): string {
  const match = datasetKey.match(/^([a-z0-9]+)_[a-z0-9]+_[a-z0-9]+_v[1-9][0-9]*$/);
  if (!match) {
    return DEFAULT_DATASET_NAME;
  }
  return match[1];
}

function validateParamValue(param: AlgorithmParamItem, rawValue: string): string | null {
  const trimmedValue = rawValue.trim();

  if (param.requiredYn === 'Y' && trimmedValue.length === 0) {
    return '필수 파라미터입니다.';
  }

  if (trimmedValue.length === 0 || !isNumericType(param.dataType)) {
    return null;
  }

  const parsedValue = Number(trimmedValue);
  if (!Number.isFinite(parsedValue)) {
    return '유효한 숫자를 입력하세요.';
  }

  if (isIntegerType(param.dataType) && !Number.isInteger(parsedValue)) {
    return '정수 값을 입력하세요.';
  }

  const minValue = toNumberOrNull(param.minValue);
  const maxValue = toNumberOrNull(param.maxValue);

  if (minValue !== null && parsedValue < minValue) {
    return `최소값 ${minValue} 이상이어야 합니다.`;
  }
  if (maxValue !== null && parsedValue > maxValue) {
    return `최대값 ${maxValue} 이하여야 합니다.`;
  }

  return null;
}

function toParamPayloadValue(param: AlgorithmParamItem, rawValue: string): string | number | null {
  const trimmedValue = rawValue.trim();
  if (!trimmedValue) {
    return null;
  }

  if (!isNumericType(param.dataType)) {
    return trimmedValue;
  }

  const parsedValue = Number(trimmedValue);
  if (!Number.isFinite(parsedValue)) {
    return null;
  }

  if (isIntegerType(param.dataType)) {
    return Math.trunc(parsedValue);
  }

  return parsedValue;
}

function parseIntegerInput(value: string): number | null {
  const text = value.trim();
  if (!text) {
    return null;
  }
  const parsed = Number(text);
  if (!Number.isFinite(parsed)) {
    return null;
  }
  return Math.trunc(parsed);
}

function statusChipColor(status: string | null | undefined): 'default' | 'success' | 'warning' | 'error' {
  const normalized = status?.toUpperCase() ?? '';
  if (normalized === 'SUCCESS') {
    return 'success';
  }
  if (normalized === 'SKIPPED' || normalized === 'RUNNING') {
    return 'warning';
  }
  if (normalized === 'FAIL') {
    return 'error';
  }
  return 'default';
}

function formatDateText(value: string | null | undefined): string {
  const text = value?.trim();
  if (!text) {
    return '-';
  }
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) {
    return text;
  }
  return date.toLocaleString();
}

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

function datasetKeyByMode(mode: LearningMode, unsupervisedDatasetKey: string): string {
  return mode === 'SUPERVISED' ? SUPERVISED_DATASET_KEY : buildDatasetIdentity(unsupervisedDatasetKey);
}

function createModeMap<T>(unsupervisedValue: T, supervisedValue: T): Record<LearningMode, T> {
  return {
    UNSUPERVISED: unsupervisedValue,
    SUPERVISED: supervisedValue,
  };
}

export function ModelTrainPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();
  const { selectedAlgorithm, setSelectedAlgorithm } = useSelectedAlgorithm();

  const navigationState = useMemo(() => {
    return (location.state ?? {}) as ModelTrainNavigationState;
  }, [location.state]);
  const stateDatasetKey = useMemo(() => extractDatasetKeyFromRouteState(location.state), [location.state]);
  const queryDatasetKey = useMemo(() => searchParams.get('datasetKey')?.trim() ?? '', [searchParams]);

  const [datasetOptions, setDatasetOptions] = useState<OperationDatasetOption[]>([]);
  const [loadingDatasetOptions, setLoadingDatasetOptions] = useState(true);
  const [datasetSelectionWarning, setDatasetSelectionWarning] = useState<string | null>(null);
  const [selectedUnsupervisedDatasetKey, setSelectedUnsupervisedDatasetKey] = useState(DEFAULT_OPERATION_DATASET_KEY);

  useEffect(() => {
    let isActive = true;
    setLoadingDatasetOptions(true);

    dataExplorationService
      .getDatasets()
      .then((rawOptions) => {
        if (!isActive) {
          return;
        }

        const operationOptions = filterOperationDatasetOptions(rawOptions ?? []);
        setDatasetOptions(operationOptions);

        const resolved = resolveOperationDatasetKeyWithFallback({
          datasetOptions: operationOptions,
          queryDatasetKey,
          stateDatasetKey,
          persistedDatasetKey: readPersistedOperationDatasetKey(),
          defaultDatasetKey: DEFAULT_OPERATION_DATASET_KEY,
        });

        setSelectedUnsupervisedDatasetKey(buildDatasetIdentity(resolved.datasetKey));
        setDatasetSelectionWarning(resolved.warning);
      })
      .catch((_error: unknown) => {
        if (!isActive) {
          return;
        }
        setDatasetOptions([]);
        setSelectedUnsupervisedDatasetKey(DEFAULT_OPERATION_DATASET_KEY);
        setDatasetSelectionWarning(`dataset 목록을 불러오지 못해 기본 dataset(${DEFAULT_OPERATION_DATASET_KEY})을 사용합니다.`);
      })
      .finally(() => {
        if (!isActive) {
          return;
        }
        setLoadingDatasetOptions(false);
      });

    return () => {
      isActive = false;
    };
  }, [queryDatasetKey, stateDatasetKey]);

  useEffect(() => {
    const normalizedDatasetKey = buildDatasetIdentity(selectedUnsupervisedDatasetKey);
    if (!normalizedDatasetKey) {
      return;
    }

    persistOperationDatasetKey(normalizedDatasetKey);
    if (queryDatasetKey === normalizedDatasetKey) {
      return;
    }

    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('datasetKey', normalizedDatasetKey);
    setSearchParams(nextParams, { replace: true });
  }, [queryDatasetKey, searchParams, selectedUnsupervisedDatasetKey, setSearchParams]);

  const [learningMode, setLearningMode] = useState<LearningMode>('UNSUPERVISED');
  const initializedLearningModeByActiveRef = useRef(false);

  const [algoCd, setAlgoCd] = useState('');
  const [algoNm, setAlgoNm] = useState('');

  const [activeSelectionsByMode, setActiveSelectionsByMode] = useState<Record<LearningMode, AlgorithmActiveSelectionData | null>>(
    createModeMap<AlgorithmActiveSelectionData | null>(null, null),
  );
  const [loadingActiveSelectionByMode, setLoadingActiveSelectionByMode] = useState<Record<LearningMode, boolean>>(
    createModeMap(false, false),
  );
  const [activeSelectionErrorByMode, setActiveSelectionErrorByMode] = useState<Record<LearningMode, string | null>>(
    createModeMap<string | null>(null, null),
  );
  const [activeSelectionWarningByMode, setActiveSelectionWarningByMode] = useState<Record<LearningMode, string | null>>(
    createModeMap<string | null>(null, null),
  );
  const [activeSelectionRefreshKey, setActiveSelectionRefreshKey] = useState(0);

  const [featureDatasets, setFeatureDatasets] = useState<FeatureDataset[]>([]);
  const [loadingFeatureDatasets, setLoadingFeatureDatasets] = useState(false);
  const [featureDatasetError, setFeatureDatasetError] = useState<string | null>(null);

  const [params, setParams] = useState<AlgorithmParamItem[]>([]);
  const [paramFormValues, setParamFormValues] = useState<ParamFormValueMap>({});
  const [loadingParams, setLoadingParams] = useState(false);
  const [paramLoadError, setParamLoadError] = useState<string | null>(null);

  const [autoPolicies, setAutoPolicies] = useState<ModelTrainAutoPolicyStatus[]>([]);
  const [schedulerEnabled, setSchedulerEnabled] = useState(true);
  const [schedulerFixedDelayMs, setSchedulerFixedDelayMs] = useState(0);
  const [loadingPolicies, setLoadingPolicies] = useState(false);
  const [policyLoadError, setPolicyLoadError] = useState<string | null>(null);

  const [autoTrainEnabled, setAutoTrainEnabled] = useState(true);
  const [schedulerIntervalSec, setSchedulerIntervalSec] = useState(String(DEFAULT_SCHEDULER_INTERVAL_SEC));
  const [minNewFeatureCount, setMinNewFeatureCount] = useState(String(DEFAULT_MIN_NEW_FEATURE_COUNT));
  const [minTotalFeatureCount, setMinTotalFeatureCount] = useState(String(DEFAULT_MIN_TOTAL_FEATURE_COUNT));
  const [recentWindowLimit, setRecentWindowLimit] = useState('');

  const [savingPolicy, setSavingPolicy] = useState(false);
  const [triggeringPolicy, setTriggeringPolicy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const [loadingResults, setLoadingResults] = useState(false);
  const [resultError, setResultError] = useState<string | null>(null);
  const [anomalyRows, setAnomalyRows] = useState<AnomalyResultRow[]>([]);
  const [loadedRunId, setLoadedRunId] = useState<string | null>(null);

  const isAdmin = user?.role.toLowerCase() === 'admin';

  const learningDatasetKey = useMemo(
    () => datasetKeyByMode(learningMode, selectedUnsupervisedDatasetKey),
    [learningMode, selectedUnsupervisedDatasetKey],
  );
  const activeSelection = useMemo(() => activeSelectionsByMode[learningMode] ?? null, [activeSelectionsByMode, learningMode]);
  const loadingActiveSelection = loadingActiveSelectionByMode[learningMode];
  const activeSelectionError = activeSelectionErrorByMode[learningMode];
  const activeSelectionWarning = activeSelectionWarningByMode[learningMode];

  const syncActiveSelectionForMode = useCallback(async (
    mode: LearningMode,
    unsupervisedDatasetKey: string,
  ): Promise<AlgorithmActiveSelectionData | null> => {
    const datasetKey = datasetKeyByMode(mode, unsupervisedDatasetKey);

    setLoadingActiveSelectionByMode((previous) => ({
      ...previous,
      [mode]: true,
    }));

    try {
      const response = await algorithmSelectionService.getSelectionOptions(datasetKey);
      const nextActiveSelection = response.activeSelection;

      setActiveSelectionsByMode((previous) => ({
        ...previous,
        [mode]: nextActiveSelection,
      }));
      setActiveSelectionWarningByMode((previous) => ({
        ...previous,
        [mode]: resolveActiveSelectionWarning(nextActiveSelection, datasetKey),
      }));
      setActiveSelectionErrorByMode((previous) => ({
        ...previous,
        [mode]: null,
      }));

      return nextActiveSelection;
    } catch (error: unknown) {
      setActiveSelectionsByMode((previous) => ({
        ...previous,
        [mode]: null,
      }));
      setActiveSelectionWarningByMode((previous) => ({
        ...previous,
        [mode]: null,
      }));
      setActiveSelectionErrorByMode((previous) => ({
        ...previous,
        [mode]: error instanceof Error ? error.message : '현재 적용 알고리즘 조회에 실패했습니다.',
      }));
      return null;
    } finally {
      setLoadingActiveSelectionByMode((previous) => ({
        ...previous,
        [mode]: false,
      }));
    }
  }, []);

  useEffect(() => {
    if (loadingDatasetOptions) {
      return;
    }

    const navigationDatasetKey = queryDatasetKey || stateDatasetKey || navigationState.dataset_key;
    let isActive = true;

    const loadActiveSelections = async () => {
      const [unsupervisedActiveSelection, supervisedActiveSelection] = await Promise.all([
        syncActiveSelectionForMode('UNSUPERVISED', selectedUnsupervisedDatasetKey),
        syncActiveSelectionForMode('SUPERVISED', selectedUnsupervisedDatasetKey),
      ]);

      if (!isActive || initializedLearningModeByActiveRef.current) {
        return;
      }

      const preferredMode = resolveInitialLearningMode(
        {
          ...navigationState,
          dataset_key: navigationDatasetKey,
        },
        selectedAlgorithm?.algoCd,
        unsupervisedActiveSelection,
        supervisedActiveSelection,
      );
      setLearningMode(preferredMode);
      initializedLearningModeByActiveRef.current = true;
    };

    void loadActiveSelections();

    return () => {
      isActive = false;
    };
  }, [
    activeSelectionRefreshKey,
    loadingDatasetOptions,
    navigationState,
    queryDatasetKey,
    selectedAlgorithm?.algoCd,
    selectedUnsupervisedDatasetKey,
    stateDatasetKey,
    syncActiveSelectionForMode,
  ]);

  useEffect(() => {
    if (!initializedLearningModeByActiveRef.current) {
      return;
    }

    void syncActiveSelectionForMode(learningMode, selectedUnsupervisedDatasetKey);
  }, [learningMode, selectedUnsupervisedDatasetKey, syncActiveSelectionForMode]);

  useEffect(() => {
    const activeAlgoCode = normalizeAlgoCode(activeSelection?.active_algo_code);
    const activeAlgoName = normalizeOptionalText(activeSelection?.active_algo_name);
    const allowedAlgoCodes = learningMode === 'SUPERVISED' ? SUPERVISED_ALGO_CODES : UNSUPERVISED_ALGO_CODES;

    let resolvedAlgoCode = activeAlgoCode && allowedAlgoCodes.includes(activeAlgoCode) ? activeAlgoCode : null;
    if (!resolvedAlgoCode) {
      resolvedAlgoCode = learningMode === 'SUPERVISED' ? 'RANDOM_FOREST' : 'ISOLATION_FOREST';
    }

    const resolvedAlgoName = activeAlgoCode === resolvedAlgoCode && activeAlgoName
      ? activeAlgoName
      : defaultAlgoName(resolvedAlgoCode);

    if (algoCd === resolvedAlgoCode && algoNm === resolvedAlgoName) {
      return;
    }

    setAlgoCd(resolvedAlgoCode);
    setAlgoNm(resolvedAlgoName);
    setSelectedAlgorithm({
      algoCd: resolvedAlgoCode,
      algoNm: resolvedAlgoName,
    });
  }, [activeSelection?.active_algo_code, activeSelection?.active_algo_name, algoCd, algoNm, learningMode, setSelectedAlgorithm]);

  const loadFeatureDatasets = useCallback(async () => {
    setLoadingFeatureDatasets(true);
    setFeatureDatasetError(null);

    try {
      const response = await modelTrainService.getFeatureDatasets();
      const datasets = response.feature_datasets ?? [];
      setFeatureDatasets(datasets);
    } catch (error: unknown) {
      setFeatureDatasets([]);
      setFeatureDatasetError(error instanceof Error ? error.message : 'Feature dataset 조회에 실패했습니다.');
    } finally {
      setLoadingFeatureDatasets(false);
    }
  }, []);

  useEffect(() => {
    void loadFeatureDatasets();
  }, [loadFeatureDatasets]);

  const selectedFeatureDataset = useMemo(() => {
    return featureDatasets.find((dataset) => buildDatasetIdentity(dataset.dataset_key) === learningDatasetKey) ?? null;
  }, [featureDatasets, learningDatasetKey]);

  const selectedDatasetOption = useMemo(() => {
    if (learningMode === 'SUPERVISED') {
      return null;
    }
    return datasetOptions.find((option) => buildDatasetIdentity(option.datasetKey) === learningDatasetKey) ?? null;
  }, [datasetOptions, learningDatasetKey, learningMode]);

  const loadPolicies = useCallback(async (datasetKey: string) => {
    setLoadingPolicies(true);
    setPolicyLoadError(null);

    try {
      const response = await modelTrainService.getAutoPolicies(datasetKey);
      setAutoPolicies(response.policies ?? []);
      setSchedulerEnabled(response.scheduler_enabled);
      setSchedulerFixedDelayMs(response.scheduler_fixed_delay_ms);
    } catch (error: unknown) {
      setAutoPolicies([]);
      setPolicyLoadError(error instanceof Error ? error.message : '학습 정책 상태를 불러오지 못했습니다.');
    } finally {
      setLoadingPolicies(false);
    }
  }, []);

  useEffect(() => {
    if (loadingDatasetOptions) {
      return;
    }
    void loadPolicies(learningDatasetKey);
  }, [learningDatasetKey, loadPolicies, loadingDatasetOptions]);

  const hasAlgorithm = !!algoCd.trim() && !!algoNm.trim();

  const selectedPolicy = useMemo(() => {
    if (!hasAlgorithm) {
      return null;
    }

    const selectedAlgoCode = algoCd.trim().toUpperCase();
    return (
      autoPolicies.find((policy) => {
        const policyAlgoCode = policy.algo_code.trim().toUpperCase();
        const policyDatasetIdentity = buildDatasetIdentity(policy.dataset_key);
        return policyAlgoCode === selectedAlgoCode && policyDatasetIdentity === learningDatasetKey;
      }) ?? null
    );
  }, [algoCd, autoPolicies, hasAlgorithm, learningDatasetKey]);

  const effectiveDatasetLabel =
    selectedFeatureDataset?.dataset_label
    ?? selectedPolicy?.dataset_label
    ?? resolveOperationDatasetLabel(selectedDatasetOption)
    ?? learningDatasetKey;
  const effectiveDatasetName =
    selectedFeatureDataset?.dataset_name?.trim()
    ?? selectedPolicy?.dataset_name?.trim()
    ?? resolveDatasetNameFromDatasetKey(learningDatasetKey);
  const effectiveEquipmentId =
    selectedFeatureDataset?.equipment_id?.trim()
    ?? selectedPolicy?.equipment_id?.trim()
    ?? '';
  const effectiveSourceCollection =
    selectedFeatureDataset?.source_collection?.trim()
    ?? selectedPolicy?.source_collection?.trim()
    ?? 'THISHMIDATA';
  const effectiveTargetCollection =
    selectedFeatureDataset?.target_collection?.trim()
    ?? selectedPolicy?.target_collection?.trim()
    ?? 'thisfeature';
  const effectiveWindowMode =
    selectedFeatureDataset?.window_mode?.trim()
    ?? selectedPolicy?.window_mode?.trim()
    ?? 'fixed_count_only';
  const effectiveFeatureStats = normalizeColumns(
    selectedFeatureDataset?.feature_stats ?? selectedPolicy?.feature_stats ?? [],
  );
  const effectiveFeatureSchedulerEnabled =
    selectedFeatureDataset?.scheduler_enabled
    ?? selectedPolicy?.scheduler_enabled
    ?? null;
  const effectiveFeatureSchedulerIntervalSec =
    selectedFeatureDataset?.scheduler_interval_sec
    ?? selectedPolicy?.scheduler_interval_sec
    ?? null;
  const effectiveFeatureLastStatus =
    selectedFeatureDataset?.last_status?.trim()
    ?? selectedPolicy?.last_status?.trim()
    ?? null;
  const effectiveFeatureLastWindowEnd =
    selectedFeatureDataset?.last_window_end
    ?? selectedPolicy?.last_window_end
    ?? null;
  const effectiveFeatureLastCheckpointValue =
    selectedFeatureDataset?.last_checkpoint_value?.trim()
    ?? selectedPolicy?.last_checkpoint_value?.trim()
    ?? null;
  const featureConfigSource =
    selectedFeatureDataset?.config_source?.trim()
    ?? selectedPolicy?.feature_config_source?.trim()
    ?? null;
  const featureConfigMessage =
    selectedFeatureDataset?.config_message?.trim()
    ?? selectedPolicy?.feature_config_message?.trim()
    ?? null;
  const payloadDatasetKey = learningDatasetKey;
  const effectiveColumns = normalizeColumns(selectedFeatureDataset?.selected_columns ?? selectedPolicy?.selected_columns);
  const effectiveWindowSize = useMemo(() => {
    if (learningMode === 'SUPERVISED') {
      const supervisedWindowSize = selectedPolicy?.window_size ?? selectedFeatureDataset?.window_size ?? 1;
      return Math.max(1, Math.floor(supervisedWindowSize));
    }

    const unsupervisedWindowSize = selectedPolicy?.window_size ?? selectedFeatureDataset?.window_size ?? DEFAULT_WINDOW_SIZE;
    return Math.max(1, Math.floor(unsupervisedWindowSize));
  }, [learningMode, selectedPolicy?.window_size, selectedFeatureDataset?.window_size]);
  const supervisedDataPrepared = learningMode === 'SUPERVISED' && effectiveColumns.length > 0;

  useEffect(() => {
    if (!algoCd.trim()) {
      setParams([]);
      setParamFormValues({});
      setParamLoadError(null);
      return;
    }

    setLoadingParams(true);
    setParamLoadError(null);

    algorithmParamService
      .getParams(algoCd.trim())
      .then((response) => {
        setParams(response);
        setParamFormValues(
          response.reduce<ParamFormValueMap>((accumulator, param) => {
            accumulator[param.paramCd] = toStringValue(param.defaultValue);
            return accumulator;
          }, {}),
        );
      })
      .catch((error: unknown) => {
        setParams([]);
        setParamFormValues({});
        setParamLoadError(error instanceof Error ? error.message : '파라미터 조회에 실패했습니다.');
      })
      .finally(() => {
        setLoadingParams(false);
      });
  }, [algoCd]);

  const visibleParams = useMemo(() => {
    const currentAlgoCode = normalizeAlgoCode(algoCd);
    if (learningMode === 'SUPERVISED' || currentAlgoCode === 'RANDOM_FOREST') {
      return params.filter((param) => RANDOM_FOREST_PARAM_CODES.has(param.paramCd.trim().toUpperCase()));
    }

    return params;
  }, [algoCd, learningMode, params]);

  useEffect(() => {
    if (params.length === 0) {
      setParamFormValues({});
      return;
    }

    setParamFormValues((previousValues) => {
      const nextValues = params.reduce<ParamFormValueMap>((accumulator, param) => {
        accumulator[param.paramCd] = toStringValue(param.defaultValue);
        return accumulator;
      }, {});

      if (selectedPolicy) {
        params.forEach((param) => {
          if (Object.prototype.hasOwnProperty.call(selectedPolicy.params, param.paramCd)) {
            nextValues[param.paramCd] = toStringValue(selectedPolicy.params[param.paramCd]);
          }
        });
        return nextValues;
      }

      params.forEach((param) => {
        const previousValue = previousValues[param.paramCd];
        if (previousValue !== undefined) {
          nextValues[param.paramCd] = previousValue;
        }
      });
      return nextValues;
    });
  }, [params, selectedPolicy]);

  useEffect(() => {
    if (!selectedPolicy) {
      setAutoTrainEnabled(true);
      setSchedulerIntervalSec(String(DEFAULT_SCHEDULER_INTERVAL_SEC));
      setMinNewFeatureCount(String(DEFAULT_MIN_NEW_FEATURE_COUNT));
      setMinTotalFeatureCount(String(DEFAULT_MIN_TOTAL_FEATURE_COUNT));
      setRecentWindowLimit('');
      return;
    }

    setAutoTrainEnabled(selectedPolicy.auto_train_enabled);
    const modelSchedulerIntervalSec =
      selectedPolicy.model_scheduler_interval_sec > 0
        ? selectedPolicy.model_scheduler_interval_sec
        : selectedPolicy.scheduler_interval_sec;
    setSchedulerIntervalSec(String(modelSchedulerIntervalSec));
    setMinNewFeatureCount(String(selectedPolicy.min_new_feature_count));
    setMinTotalFeatureCount(String(selectedPolicy.min_total_feature_count));
    setRecentWindowLimit(selectedPolicy.recent_window_limit === null ? '' : String(selectedPolicy.recent_window_limit));
  }, [selectedPolicy]);

  const paramErrors = useMemo(() => {
    return visibleParams.reduce<ParamValidationErrorMap>((accumulator, param) => {
      accumulator[param.paramCd] = validateParamValue(param, paramFormValues[param.paramCd] ?? '');
      return accumulator;
    }, {});
  }, [paramFormValues, visibleParams]);

  const hasParamValidationError = useMemo(() => {
    return Object.values(paramErrors).some((error) => error !== null);
  }, [paramErrors]);

  const paramsPayload = useMemo(() => {
    const payload = params.reduce<Record<string, unknown>>((accumulator, param) => {
      const paramCode = param.paramCd;
      const currentValue = paramFormValues[paramCode];

      if (currentValue !== undefined) {
        accumulator[paramCode] = toParamPayloadValue(param, currentValue);
        return accumulator;
      }

      if (selectedPolicy && Object.prototype.hasOwnProperty.call(selectedPolicy.params, paramCode)) {
        accumulator[paramCode] = selectedPolicy.params[paramCode];
        return accumulator;
      }

      accumulator[paramCode] = toParamPayloadValue(param, toStringValue(param.defaultValue));
      return accumulator;
    }, {});

    if (selectedPolicy) {
      Object.entries(selectedPolicy.params).forEach(([key, value]) => {
        const normalizedKey = key.trim();
        if (!normalizedKey || Object.prototype.hasOwnProperty.call(payload, normalizedKey)) {
          return;
        }
        payload[normalizedKey] = value;
      });
    }

    return payload;
  }, [paramFormValues, params, selectedPolicy]);

  const paramsPayloadIsEmpty = useMemo(() => Object.keys(paramsPayload).length === 0, [paramsPayload]);

  const policyParamSaveBlockedMessage = useMemo(() => {
    if (!hasAlgorithm || loadingParams) {
      return null;
    }
    if (paramLoadError) {
      return '파라미터 정의 정보를 불러오지 못해 정책을 저장할 수 없습니다.';
    }
    if (visibleParams.length === 0) {
      return '파라미터 정의 정보가 없어 정책을 저장할 수 없습니다.';
    }
    if (paramsPayloadIsEmpty) {
      return '파라미터 스냅샷이 비어 있어 정책을 저장할 수 없습니다.';
    }
    return null;
  }, [hasAlgorithm, loadingParams, paramLoadError, paramsPayloadIsEmpty, visibleParams.length]);

  const schedulerIntervalSecValue = parseIntegerInput(schedulerIntervalSec);
  const minNewFeatureCountValue = parseIntegerInput(minNewFeatureCount);
  const minTotalFeatureCountValue = parseIntegerInput(minTotalFeatureCount);
  const recentWindowLimitValue = parseIntegerInput(recentWindowLimit);

  const policyInputError = useMemo(() => {
    if (schedulerIntervalSecValue === null || schedulerIntervalSecValue <= 0) {
      return 'model_scheduler_interval_sec는 1 이상이어야 합니다.';
    }
    if (minNewFeatureCountValue === null || minNewFeatureCountValue < 0) {
      return 'min_new_feature_count는 0 이상이어야 합니다.';
    }
    if (minTotalFeatureCountValue === null || minTotalFeatureCountValue <= 0) {
      return 'min_total_feature_count는 1 이상이어야 합니다.';
    }
    if (recentWindowLimit.trim().length > 0 && (recentWindowLimitValue === null || recentWindowLimitValue <= 0)) {
      return 'recent_window_limit은 비우거나 1 이상의 값을 입력해야 합니다.';
    }
    return null;
  }, [
    minNewFeatureCountValue,
    minTotalFeatureCountValue,
    recentWindowLimit,
    recentWindowLimitValue,
    schedulerIntervalSecValue,
  ]);

  const canSavePolicy =
    hasAlgorithm
    && effectiveColumns.length > 0
    && !loadingParams
    && !policyParamSaveBlockedMessage
    && !hasParamValidationError
    && policyInputError === null
    && !savingPolicy;

  const canTriggerNow = !!selectedPolicy && isAdmin && !triggeringPolicy;

  const policyPayload = useMemo(() => {
    const payload: {
      dataset_name: string;
      dataset_key: string;
      equipment_id?: string;
      selected_columns: string[];
      window_size: number;
      algo_code: string;
      algo_name: string;
      params: Record<string, unknown>;
      auto_train_enabled: boolean;
      scheduler_interval_sec: number | null;
      min_new_feature_count: number | null;
      min_total_feature_count: number | null;
      recent_window_limit: number | null;
    } = {
      dataset_name: effectiveDatasetName,
      dataset_key: payloadDatasetKey,
      selected_columns: effectiveColumns,
      window_size: effectiveWindowSize,
      algo_code: algoCd.trim(),
      algo_name: algoNm.trim(),
      params: paramsPayload,
      auto_train_enabled: autoTrainEnabled,
      scheduler_interval_sec: schedulerIntervalSecValue,
      min_new_feature_count: minNewFeatureCountValue,
      min_total_feature_count: minTotalFeatureCountValue,
      recent_window_limit: recentWindowLimit.trim() ? recentWindowLimitValue : null,
    };

    if (effectiveEquipmentId.trim()) {
      payload.equipment_id = effectiveEquipmentId.trim();
    }

    return payload;
  }, [
    algoCd,
    algoNm,
    autoTrainEnabled,
    effectiveColumns,
    effectiveEquipmentId,
    effectiveWindowSize,
    minNewFeatureCountValue,
    minTotalFeatureCountValue,
    paramsPayload,
    payloadDatasetKey,
    effectiveDatasetName,
    recentWindowLimit,
    recentWindowLimitValue,
    schedulerIntervalSecValue,
  ]);

  const triggerPayloadPreview = useMemo(() => {
    if (!selectedPolicy) {
      return null;
    }

    return {
      policy_id: selectedPolicy.policy_id,
      dataset_key: buildDatasetIdentity(selectedPolicy.dataset_key),
      algo_code: selectedPolicy.algo_code,
      requested_by_role: user?.role ?? 'user',
      force_run: true,
    };
  }, [selectedPolicy, user?.role]);

  const loadAnomalyResults = useCallback(async (runId: string) => {
    const normalizedRunId = runId.trim();
    if (!normalizedRunId) {
      setLoadedRunId(null);
      setAnomalyRows([]);
      setResultError(null);
      return;
    }

    setLoadingResults(true);
    setResultError(null);

    try {
      const response = await modelTrainService.getAnomalyResults(normalizedRunId, RESULT_LIMIT);
      setLoadedRunId(normalizedRunId);
      setAnomalyRows(response.anomalyResults);
    } catch (error: unknown) {
      setLoadedRunId(normalizedRunId);
      setAnomalyRows([]);
      setResultError(error instanceof Error ? error.message : 'Anomaly result 조회에 실패했습니다.');
    } finally {
      setLoadingResults(false);
    }
  }, []);

  useEffect(() => {
    if (learningMode === 'SUPERVISED') {
      setLoadedRunId(null);
      setAnomalyRows([]);
      setResultError(null);
      return;
    }

    const lastRunId = selectedPolicy?.last_run_id?.trim() ?? '';
    if (!lastRunId) {
      setLoadedRunId(null);
      setAnomalyRows([]);
      setResultError(null);
      return;
    }
    void loadAnomalyResults(lastRunId);
  }, [learningMode, loadAnomalyResults, selectedPolicy?.last_run_id, selectedPolicy?.policy_id]);

  const handleSavePolicy = async () => {
    if (policyParamSaveBlockedMessage) {
      setActionError(policyParamSaveBlockedMessage);
      return;
    }

    if (effectiveColumns.length === 0) {
      setActionError(
        learningMode === 'SUPERVISED'
          ? '지도 학습 입력 feature columns가 비어 있습니다. tmst_feature_mst.selected_columns를 확인하세요.'
          : '비지도 학습 대상 feature dataset selected_columns가 비어 있습니다.',
      );
      return;
    }

    if (!canSavePolicy || schedulerIntervalSecValue === null || minNewFeatureCountValue === null || minTotalFeatureCountValue === null) {
      setActionError(policyInputError ?? '입력값 또는 파라미터 상태를 확인하세요.');
      return;
    }

    if (paramsPayloadIsEmpty) {
      setActionError('파라미터 스냅샷이 비어 있어 정책을 저장할 수 없습니다.');
      return;
    }

    setSavingPolicy(true);
    setActionError(null);
    setActionMessage(null);

    try {
      const requestPayload: {
        dataset_name: string;
        dataset_key: string;
        equipment_id?: string;
        selected_columns: string[];
        window_size: number;
        algo_code: string;
        algo_name: string;
        params: Record<string, unknown>;
        auto_train_enabled: boolean;
        scheduler_interval_sec: number;
        min_new_feature_count: number;
        min_total_feature_count: number;
        recent_window_limit?: number | null;
      } = {
        dataset_name: effectiveDatasetName,
        dataset_key: payloadDatasetKey,
        selected_columns: effectiveColumns,
        window_size: effectiveWindowSize,
        algo_code: algoCd.trim(),
        algo_name: algoNm.trim(),
        params: paramsPayload,
        auto_train_enabled: autoTrainEnabled,
        scheduler_interval_sec: schedulerIntervalSecValue,
        min_new_feature_count: minNewFeatureCountValue,
        min_total_feature_count: minTotalFeatureCountValue,
        recent_window_limit: recentWindowLimit.trim() ? recentWindowLimitValue : null,
      };
      if (effectiveEquipmentId.trim()) {
        requestPayload.equipment_id = effectiveEquipmentId.trim();
      }

      const savedPolicy = await modelTrainService.upsertAutoPolicy(requestPayload);

      setActionMessage(`학습 정책이 저장되었습니다. policy_id: ${savedPolicy.policy_id}`);
      await loadPolicies(learningDatasetKey);
      setActiveSelectionRefreshKey((previous) => previous + 1);

      if (learningMode === 'UNSUPERVISED' && savedPolicy.last_run_id?.trim()) {
        await loadAnomalyResults(savedPolicy.last_run_id);
      }
    } catch (error: unknown) {
      setActionError(error instanceof Error ? error.message : '학습 정책 저장에 실패했습니다.');
    } finally {
      setSavingPolicy(false);
    }
  };

  const handleTriggerNow = async () => {
    if (!isAdmin) {
      setActionError('지금 실행(관리자)은 admin만 사용할 수 있습니다.');
      return;
    }

    if (!selectedPolicy) {
      setActionError('먼저 학습 정책을 저장하세요.');
      return;
    }

    setTriggeringPolicy(true);
    setActionError(null);
    setActionMessage(null);

    try {
      const triggerResponse = await modelTrainService.triggerAutoPolicies({
        policy_id: selectedPolicy.policy_id,
        dataset_key: buildDatasetIdentity(selectedPolicy.dataset_key),
        algo_code: selectedPolicy.algo_code,
        requested_by_role: user?.role ?? 'user',
        force_run: true,
      });

      const firstResult = triggerResponse.results[0];
      const runIdText = firstResult?.run_id ? `, run_id=${firstResult.run_id}` : '';
      setActionMessage(
        `지금 실행 결과: 요청=${triggerResponse.requested_policy_count}, 실행=${triggerResponse.executed_policy_count}, 성공=${triggerResponse.success_policy_count}${runIdText}`,
      );

      if (learningMode === 'UNSUPERVISED' && firstResult?.run_id) {
        await loadAnomalyResults(firstResult.run_id);
      }

      await loadPolicies(learningDatasetKey);
    } catch (error: unknown) {
      setActionError(error instanceof Error ? error.message : '지금 실행(관리자)에 실패했습니다.');
    } finally {
      setTriggeringPolicy(false);
    }
  };

  const activeModeTitle = learningMode === 'SUPERVISED' ? '지도 학습 적용 알고리즘' : '비지도 학습 적용 알고리즘';
  const activeDatasetTitle = learningMode === 'SUPERVISED' ? SUPERVISED_DATASET_KEY : learningDatasetKey;

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h4">Model Training / Run Policy</Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
          Review the active synthetic policy, feature dataset summary, recent demo runs, metrics, and deterministic demo execution status.
        </Typography>

        <Tabs
          value={learningMode}
          onChange={(_event, nextMode: LearningMode) => {
            setLearningMode(nextMode);
            setActionError(null);
            setActionMessage(null);
          }}
          sx={{ mt: 1.2 }}
        >
          <Tab label="비지도 학습" value="UNSUPERVISED" />
          <Tab label="지도 학습" value="SUPERVISED" />
        </Tabs>

        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={1}
          alignItems={{ xs: 'stretch', md: 'center' }}
          sx={{ mt: 1.2, maxWidth: 760 }}
        >
          <TextField
            select
            label="운영 Dataset"
            value={selectedUnsupervisedDatasetKey}
            onChange={(event) => {
              setSelectedUnsupervisedDatasetKey(buildDatasetIdentity(event.target.value));
              setLearningMode('UNSUPERVISED');
              setActionError(null);
              setActionMessage(null);
            }}
            disabled={loadingDatasetOptions || datasetOptions.length === 0}
            fullWidth
          >
            {datasetOptions.map((option) => (
              <MenuItem key={option.datasetKey} value={option.datasetKey}>
                {resolveOperationDatasetLabel(option)} ({option.datasetKey})
              </MenuItem>
            ))}
          </TextField>
        </Stack>

        {loadingActiveSelection ? (
          <Chip size="small" label="활성 알고리즘 동기화 중..." sx={{ mt: 1 }} />
        ) : activeSelection ? (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
            <Chip
              size="small"
              color="success"
              label={`${activeModeTitle}: ${activeSelection.active_algo_name ?? activeSelection.active_algo_code ?? '알 수 없음'}`}
            />
            <Chip size="small" variant="outlined" label={`dataset: ${activeSelection.dataset_key ?? activeDatasetTitle}`} />
          </Stack>
        ) : (
          <Chip size="small" color="warning" label={`${activeModeTitle}: 적용된 알고리즘 없음`} sx={{ mt: 1 }} />
        )}
      </Box>

      {activeSelectionError && <Alert severity="error">{activeSelectionError}</Alert>}
      {activeSelectionWarning && <Alert severity="warning">{activeSelectionWarning}</Alert>}
      {datasetSelectionWarning && <Alert severity="warning">{datasetSelectionWarning}</Alert>}

      {!loadingActiveSelection && !activeSelectionError && !activeSelection && (
        <Alert
          severity="warning"
          action={(
            <Button
              size="small"
              variant="outlined"
              onClick={() => navigate(`/operation/algorithm?datasetKey=${encodeURIComponent(learningDatasetKey)}`)}
            >
              알고리즘 선택 이동
            </Button>
          )}
        >
          tmst_model_active에 현재 적용된 알고리즘이 없습니다. 알고리즘 선택 화면에서 적용 후 다시 진입하세요.
        </Alert>
      )}

      <Card variant="outlined">
        <CardContent>
          <Typography variant="subtitle1" fontWeight={700}>
            Feature dataset 설정 (tmst_feature_mst 기준)
          </Typography>
          <Divider sx={{ my: 1.2 }} />

          {learningMode === 'UNSUPERVISED' ? (
            <Alert severity="info" sx={{ mb: 1.2 }}>
              비지도 학습은 feature 기반 이상 탐지를 수행합니다. thisfeature 컬렉션 기반으로 학습됩니다.
            </Alert>
          ) : (
            <Stack spacing={1.2} sx={{ mb: 1.2 }}>
              <Alert severity="warning">
                Demo 운영 기준 라벨 데이터는 아직 미준비 상태입니다. Random Forest 자동 스케줄 실행은 비활성입니다.
              </Alert>
              <Alert severity="info">지도 학습은 라벨 기반 분류 모델(Random Forest)을 수행합니다.</Alert>
              <Alert severity="info">label=0/1 데이터만 학습 및 평가에 사용합니다.</Alert>
              <Alert severity="info">label=9 데이터는 학습에서 제외됩니다.</Alert>
            </Stack>
          )}

          {learningMode === 'UNSUPERVISED' && loadingFeatureDatasets && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress size={28} />
            </Box>
          )}

          {learningMode === 'UNSUPERVISED' && !loadingFeatureDatasets && featureDatasetError && <Alert severity="error">{featureDatasetError}</Alert>}

          {learningMode === 'UNSUPERVISED' && !loadingFeatureDatasets && !featureDatasetError && !selectedFeatureDataset && !selectedPolicy && (
            <Alert severity="warning">
              비지도 학습 dataset({learningDatasetKey})에 연결된 feature dataset이 없습니다. 전처리 자동 생성(thisfeature) 상태를 먼저 확인하세요.
            </Alert>
          )}

          {learningMode === 'SUPERVISED' && featureDatasetError && (
            <Alert severity="warning" sx={{ mb: 1.2 }}>
              feature dataset 조회 실패: {featureDatasetError}
            </Alert>
          )}

          {featureConfigSource === 'TMST_DATASET_CONFIG_FALLBACK' && (
            <Alert severity="warning" sx={{ mb: 1.2 }}>
              tmst_feature_mst가 없어 tmst_dataset_config fallback을 사용 중입니다.
            </Alert>
          )}
          {featureConfigMessage && (
            <Alert severity={featureConfigSource === 'TMST_DATASET_CONFIG_FALLBACK' ? 'warning' : 'info'} sx={{ mb: 1.2 }}>
              {featureConfigMessage}
            </Alert>
          )}

          {(learningMode === 'SUPERVISED' || selectedFeatureDataset || selectedPolicy) && (
            <Stack spacing={1.5}>
              <Box
                sx={{
                  display: 'grid',
                  gap: 1.2,
                  gridTemplateColumns: {
                    xs: '1fr',
                    sm: 'repeat(2, minmax(0, 1fr))',
                  },
                }}
              >
                <TextField label="dataset_key" value={payloadDatasetKey} InputProps={{ readOnly: true }} fullWidth />
                <TextField label="dataset_label" value={effectiveDatasetLabel} InputProps={{ readOnly: true }} fullWidth />
                <TextField label="dataset_name" value={effectiveDatasetName} InputProps={{ readOnly: true }} fullWidth />
                <TextField label="equipment_id" value={effectiveEquipmentId} InputProps={{ readOnly: true }} fullWidth />
                <TextField label="source_collection" value={effectiveSourceCollection} InputProps={{ readOnly: true }} fullWidth />
                <TextField label="target_collection" value={effectiveTargetCollection} InputProps={{ readOnly: true }} fullWidth />
                <TextField label="window_mode" value={effectiveWindowMode} InputProps={{ readOnly: true }} fullWidth />
                <TextField label="window_size" value={effectiveWindowSize} InputProps={{ readOnly: true }} fullWidth />
                <TextField
                  label="scheduler_enabled"
                  value={
                    effectiveFeatureSchedulerEnabled === null
                      ? '-'
                      : effectiveFeatureSchedulerEnabled
                        ? 'Y'
                        : 'N'
                  }
                  InputProps={{ readOnly: true }}
                  fullWidth
                />
                <TextField
                  label="scheduler_interval_sec"
                  value={
                    effectiveFeatureSchedulerIntervalSec == null || effectiveFeatureSchedulerIntervalSec <= 0
                      ? '-'
                      : String(effectiveFeatureSchedulerIntervalSec)
                  }
                  InputProps={{ readOnly: true }}
                  fullWidth
                />
                {learningMode === 'SUPERVISED' && (
                  <>
                    <TextField
                      label="source collection"
                      value={supervisedDataPrepared ? 'thisrawlabeled' : '라벨 데이터 미준비'}
                      InputProps={{ readOnly: true }}
                      fullWidth
                    />
                    <TextField label="label rule" value="label=0/1 사용, label=9 제외" InputProps={{ readOnly: true }} fullWidth />
                  </>
                )}
                {learningMode === 'UNSUPERVISED' && (
                  <TextField label="source collection" value="thisfeature" InputProps={{ readOnly: true }} fullWidth />
                )}
              </Box>

              <Box>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 0.8 }}>
                  selected_columns
                </Typography>
                {effectiveColumns.length === 0 ? (
                  <Alert severity="warning" sx={{ mt: 0.6 }}>
                    {learningMode === 'SUPERVISED'
                      ? '지도 학습 입력 feature columns가 없습니다. tmst_feature_mst.selected_columns를 확인하세요.'
                      : '비지도 학습 대상 selected_columns가 비어 있습니다.'}
                  </Alert>
                ) : (
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {effectiveColumns.map((column) => (
                      <Chip key={column} size="small" label={column} />
                    ))}
                  </Stack>
                )}
              </Box>

              <Box>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 0.8 }}>
                  feature_stats
                </Typography>
                {effectiveFeatureStats.length === 0 ? (
                  <Alert severity="info">feature_stats 정보가 없습니다.</Alert>
                ) : (
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {effectiveFeatureStats.map((statKey) => (
                      <Chip key={statKey} size="small" label={statKey} variant="outlined" />
                    ))}
                  </Stack>
                )}
              </Box>

              <Box
                sx={{
                  display: 'grid',
                  gap: 1,
                  gridTemplateColumns: {
                    xs: '1fr',
                    md: 'repeat(2, minmax(0, 1fr))',
                  },
                }}
              >
                <TextField label="last_status" value={effectiveFeatureLastStatus ?? '-'} InputProps={{ readOnly: true }} fullWidth />
                <TextField
                  label="last_window_end"
                  value={effectiveFeatureLastWindowEnd ?? '-'}
                  InputProps={{ readOnly: true }}
                  fullWidth
                />
                <TextField
                  label="last_checkpoint_value"
                  value={effectiveFeatureLastCheckpointValue ?? '-'}
                  InputProps={{ readOnly: true }}
                  fullWidth
                />
                <TextField label="config_source" value={featureConfigSource ?? '-'} InputProps={{ readOnly: true }} fullWidth />
              </Box>
            </Stack>
          )}
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="subtitle1" fontWeight={700}>
            정책 파라미터
          </Typography>
          <Divider sx={{ my: 1.2 }} />

          {learningMode === 'SUPERVISED' && (
            <Alert severity="info" sx={{ mb: 1.2 }}>
              RF 전용 파라미터만 표시합니다. contamination / latent_dim / sequence_length / hidden_units는 노출하지 않습니다.
            </Alert>
          )}

          {loadingParams && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress size={28} />
            </Box>
          )}

          {!loadingParams && paramLoadError && <Alert severity="error">{paramLoadError}</Alert>}

          {!loadingParams && !paramLoadError && !hasAlgorithm && (
            <Alert severity="info">알고리즘 선택 후 파라미터를 조회할 수 있습니다.</Alert>
          )}

          {!loadingParams && !paramLoadError && hasAlgorithm && visibleParams.length === 0 && (
            <Alert severity="info">선택된 알고리즘에 매핑된 파라미터가 없습니다.</Alert>
          )}

          {!loadingParams && !paramLoadError && visibleParams.length > 0 && (
            <Stack spacing={1.2}>
              {visibleParams.map((param) => {
                const value = paramFormValues[param.paramCd] ?? '';
                const error = paramErrors[param.paramCd];
                const helperText = [
                  param.desc ? `Description: ${param.desc}` : null,
                  `min: ${toStringValue(param.minValue) || '-'}`,
                  `max: ${toStringValue(param.maxValue) || '-'}`,
                  `step: ${toStringValue(param.step) || '-'}`,
                  `uiType: ${toStringValue(param.uiType) || '-'}`,
                ]
                  .filter((item) => item !== null)
                  .join(' | ');

                return (
                  <Box
                    key={param.paramCd}
                    sx={{
                      border: '1px solid #d6deea',
                      borderRadius: 2,
                      p: 1.2,
                    }}
                  >
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                      <Typography variant="subtitle2" fontWeight={700}>
                        {param.paramNm}
                      </Typography>
                      <Chip size="small" label={param.paramCd} />
                      <Chip size="small" variant="outlined" label={param.dataType} />
                      {param.requiredYn === 'Y' && <Chip size="small" color="error" label="필수" />}
                    </Stack>
                    <TextField
                      sx={{ mt: 1 }}
                      label="Value"
                      type={isNumericType(param.dataType) ? 'number' : 'text'}
                      value={value}
                      required={param.requiredYn === 'Y'}
                      onChange={(event) => {
                        setParamFormValues((previousValues) => ({
                          ...previousValues,
                          [param.paramCd]: event.target.value,
                        }));
                      }}
                      error={error !== null}
                      helperText={error ?? helperText}
                      fullWidth
                    />
                  </Box>
                );
              })}
            </Stack>
          )}
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="subtitle1" fontWeight={700}>
            Model policy 설정 (tmst_model_policy)
          </Typography>
          <Divider sx={{ my: 1.2 }} />
          <Alert severity="info" sx={{ mb: 1.2 }}>
            Feature 설정(window_size/selected_columns)은 전처리 화면에서 관리되며 tmst_feature_mst를 source of truth로 사용합니다.
          </Alert>

          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 1.2 }}>
            <Chip label={schedulerEnabled ? '스케줄러 활성' : '스케줄러 비활성'} color={schedulerEnabled ? 'success' : 'default'} />
            <Chip label={`fixed-delay: ${schedulerFixedDelayMs} ms`} variant="outlined" />
            {selectedPolicy && <Chip label={`policy_id: ${selectedPolicy.policy_id}`} variant="outlined" />}
          </Stack>

          <Box
            sx={{
              display: 'grid',
              gap: 1.2,
              mt: 0.4,
              mb: 1.2,
              gridTemplateColumns: {
                xs: '1fr',
                md: 'repeat(2, minmax(0, 1fr))',
              },
            }}
          >
            <TextField label="algo_code" value={algoCd} InputProps={{ readOnly: true }} fullWidth />
            <TextField label="algo_name" value={algoNm} InputProps={{ readOnly: true }} fullWidth />
            <TextField label="dataset_key" value={payloadDatasetKey} InputProps={{ readOnly: true }} fullWidth />
            <TextField label="dataset_label" value={effectiveDatasetLabel} InputProps={{ readOnly: true }} fullWidth />
          </Box>

          <FormControlLabel
            control={<Switch checked={autoTrainEnabled} onChange={(event) => setAutoTrainEnabled(event.target.checked)} />}
            label="auto_train_enabled"
          />

          <Box
            sx={{
              display: 'grid',
              gap: 1.2,
              mt: 0.8,
              gridTemplateColumns: {
                xs: '1fr',
                md: 'repeat(2, minmax(0, 1fr))',
              },
            }}
          >
            <TextField
              label="model_scheduler_interval_sec"
              type="number"
              value={schedulerIntervalSec}
              onChange={(event) => setSchedulerIntervalSec(event.target.value)}
              fullWidth
            />
            <TextField
              label="min_new_feature_count"
              type="number"
              value={minNewFeatureCount}
              onChange={(event) => setMinNewFeatureCount(event.target.value)}
              fullWidth
            />
            <TextField
              label="min_total_feature_count"
              type="number"
              value={minTotalFeatureCount}
              onChange={(event) => setMinTotalFeatureCount(event.target.value)}
              fullWidth
            />
            <TextField
              label="recent_window_limit (optional)"
              type="number"
              value={recentWindowLimit}
              onChange={(event) => setRecentWindowLimit(event.target.value)}
              helperText="비워두면 전체 window를 사용합니다."
              fullWidth
            />
          </Box>

          {policyInputError && (
            <Alert severity="warning" sx={{ mt: 1.2 }}>
              {policyInputError}
            </Alert>
          )}
          {policyParamSaveBlockedMessage && (
            <Alert severity="warning" sx={{ mt: 1.2 }}>
              {policyParamSaveBlockedMessage}
            </Alert>
          )}

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1.2 }}>
            <Button variant="contained" disabled={!canSavePolicy} onClick={() => void handleSavePolicy()}>
              {savingPolicy ? '저장 중...' : '학습 정책 저장'}
            </Button>
            <Button variant="outlined" disabled={!canTriggerNow} onClick={() => void handleTriggerNow()}>
              {triggeringPolicy ? 'Running Demo Model...' : 'Run Demo Model'}
            </Button>
            <Button variant="text" onClick={() => void loadPolicies(learningDatasetKey)} disabled={loadingPolicies}>
              상태 새로고침
            </Button>
          </Stack>

          {!isAdmin && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.8 }}>
              현재 계정 role={user?.role ?? 'unknown'} 이므로 수동 실행은 비활성화됩니다.
            </Typography>
          )}
          {actionMessage && (
            <Alert severity="success" sx={{ mt: 1.2 }}>
              {actionMessage}
            </Alert>
          )}
          {actionError && (
            <Alert severity="error" sx={{ mt: 1.2 }}>
              {actionError}
            </Alert>
          )}

          {loadingPolicies && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
              <CircularProgress size={26} />
            </Box>
          )}

          {!loadingPolicies && policyLoadError && (
            <Alert severity="error" sx={{ mt: 1.2 }}>
              {policyLoadError}
            </Alert>
          )}

          {!loadingPolicies && !policyLoadError && selectedPolicy && (
            <Box sx={{ mt: 1.4 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.8 }}>
                정책 상태
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={`last_train_status: ${selectedPolicy.last_train_status ?? '-'}`} color={statusChipColor(selectedPolicy.last_train_status)} />
                <Chip label={`last_run_id: ${selectedPolicy.last_run_id ?? '-'}`} variant="outlined" />
                <Chip label={`pending_new_feature_count: ${selectedPolicy.pending_new_feature_count}`} variant="outlined" />
                <Chip label={`total_feature_count: ${selectedPolicy.total_feature_count}`} variant="outlined" />
              </Stack>

              <Box
                sx={{
                  mt: 1,
                  display: 'grid',
                  gap: 0.6,
                  gridTemplateColumns: {
                    xs: '1fr',
                    md: 'repeat(2, minmax(0, 1fr))',
                  },
                }}
              >
                <Typography variant="body2">last_train_at: {formatDateText(selectedPolicy.last_train_at)}</Typography>
                <Typography variant="body2">last_train_window_end: {formatDateText(selectedPolicy.last_train_window_end)}</Typography>
                <Typography variant="body2">last_checked_at: {formatDateText(selectedPolicy.last_checked_at)}</Typography>
                <Typography variant="body2">run_history_count: {selectedPolicy.run_history_count}</Typography>
              </Box>

              <Box sx={{ mt: 1 }}>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 0.6 }}>
                  최근 사용 파라미터
                </Typography>
                <Box
                  component="pre"
                  sx={{
                    m: 0,
                    p: 1,
                    borderRadius: 1.5,
                    border: '1px solid #d6deea',
                    backgroundColor: '#f8fbff',
                    overflowX: 'auto',
                    fontSize: 12,
                  }}
                >
                  {JSON.stringify(selectedPolicy.params, null, 2)}
                </Box>
              </Box>

              {selectedPolicy.last_skip_reason && (
                <Alert severity="info" sx={{ mt: 1.1 }}>
                  최근 skip 사유: {selectedPolicy.last_skip_reason}
                </Alert>
              )}

              {selectedPolicy.last_error_message && (
                <Alert severity="warning" sx={{ mt: 1.1 }}>
                  최근 에러: {selectedPolicy.last_error_message}
                </Alert>
              )}
            </Box>
          )}

          {!loadingPolicies && !policyLoadError && !selectedPolicy && (
            <Alert severity="info" sx={{ mt: 1.2 }}>
              현재 선택된 dataset/algo 조합에 저장된 자동 학습 정책이 없습니다. &quot;학습 정책 저장&quot;을 먼저 수행하세요.
            </Alert>
          )}
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="subtitle1" fontWeight={700}>
            Payload Preview
          </Typography>
          <Divider sx={{ my: 1.2 }} />

          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.8 }}>
            학습 정책 저장 Payload
          </Typography>
          <Alert severity="info" sx={{ mb: 1 }}>
            payload의 selected_columns/window_size는 tmst_model_policy 저장값이 아니라 tmst_feature_mst 스냅샷 전달용입니다.
          </Alert>
          <Box
            component="pre"
            sx={{
              m: 0,
              p: 1.2,
              borderRadius: 2,
              border: '1px solid #d6deea',
              backgroundColor: '#f8fbff',
              overflowX: 'auto',
              fontSize: 12,
            }}
          >
            {JSON.stringify(policyPayload, null, 2)}
          </Box>
          <Typography variant="subtitle2" fontWeight={700} sx={{ mt: 1.4, mb: 0.8 }}>
            지금 실행(관리자) Payload
          </Typography>
          {triggerPayloadPreview ? (
            <Box
              component="pre"
              sx={{
                m: 0,
                p: 1.2,
                borderRadius: 2,
                border: '1px solid #d6deea',
                backgroundColor: '#f8fbff',
                overflowX: 'auto',
                fontSize: 12,
              }}
            >
              {JSON.stringify(triggerPayloadPreview, null, 2)}
            </Box>
          ) : (
            <Alert severity="info">저장된 policy_id가 있을 때 지금 실행 payload를 표시합니다.</Alert>
          )}
        </CardContent>
      </Card>

      {learningMode === 'SUPERVISED' ? (
        <Card variant="outlined">
          <CardContent>
            <Typography variant="subtitle1" fontWeight={700}>
              지도 학습 실행 흐름
            </Typography>
            <Divider sx={{ my: 1.2 }} />
            <Alert severity="info" sx={{ mb: 1.1 }}>
              Random Forest 지도 학습은 IF/AE anomaly pipeline과 분리 운영됩니다.
            </Alert>
            <Stack spacing={0.7}>
              <Typography variant="body2">{supervisedDataPrepared ? 'thisrawlabeled' : '라벨 데이터 미준비'}</Typography>
              <Typography variant="body2">→ Random Forest 학습</Typography>
              <Typography variant="body2">→ thisclassificationresult 저장</Typography>
              <Typography variant="body2">→ thismodeleval 저장</Typography>
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.2 }}>
              thisanomalyresult는 비지도(IF/AE) 전용 결과 저장소이며 RF 결과는 저장하지 않습니다.
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <Card variant="outlined">
          <CardContent>
            <Typography variant="subtitle1" fontWeight={700}>
              학습 결과 (Anomaly Result)
            </Typography>
            <Divider sx={{ my: 1.2 }} />

            {selectedPolicy?.last_run_id && (
              <Button variant="text" sx={{ mb: 1.1 }} onClick={() => void loadAnomalyResults(selectedPolicy.last_run_id ?? '')}>
                결과 새로고침
              </Button>
            )}

            {loadingResults && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress size={28} />
              </Box>
            )}

            {!loadingResults && resultError && <Alert severity="error">{resultError}</Alert>}

            {!loadingResults && !resultError && !selectedPolicy && (
              <Alert severity="info">정책 저장 이후 실행 이력이 생성되면 anomaly result를 표시합니다.</Alert>
            )}

            {!loadingResults && !resultError && selectedPolicy && !selectedPolicy.last_run_id && (
              <Alert severity="info">아직 실행 이력이 없습니다. 스케줄 조건 충족 또는 지금 실행(관리자) 후 결과를 확인하세요.</Alert>
            )}

            {!loadingResults && !resultError && selectedPolicy?.last_run_id && anomalyRows.length === 0 && (
              <Alert severity="info">run_id({loadedRunId ?? selectedPolicy.last_run_id})에 연결된 anomaly result가 없습니다.</Alert>
            )}

            {!loadingResults && !resultError && anomalyRows.length > 0 && (
              <TableContainer sx={{ border: '1px solid #d6deea', borderRadius: 2, maxHeight: 520 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700 }}>run_id</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>window_start</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>window_end</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>anomaly_score</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>is_anomaly</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {anomalyRows.map((row, index) => (
                      <TableRow key={`${row.run_id}-${row.window_start}-${row.window_end}-${index}`} hover>
                        <TableCell>{formatCellValue(row.run_id)}</TableCell>
                        <TableCell>{formatCellValue(row.window_start)}</TableCell>
                        <TableCell>{formatCellValue(row.window_end)}</TableCell>
                        <TableCell>{formatCellValue(row.anomaly_score)}</TableCell>
                        <TableCell>{formatCellValue(row.is_anomaly)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </CardContent>
        </Card>
      )}
    </Stack>
  );
}

