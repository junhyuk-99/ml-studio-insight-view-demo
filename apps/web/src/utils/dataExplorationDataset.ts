import type {
  DataExplorationAppliedMatchFilter,
  DataExplorationDatasetOption,
} from '../types/dataExploration';

const DATA_EXPLORATION_DATASET_STORAGE_KEY = 'data-exploration.selectedDatasetKey';
const DEFAULT_DATASET_KEY = 'DEMO_DATASET_MANUFACTURING_AI';
const LEGACY_GLOBAL_DATASET_KEY = 'demo_hmi_all_default_v1';
const DEPRECATED_RUNTIME_DATASET_KEY = 'thisraw_all_default_v1';
const REQUIRED_DATASET_PURPOSE = 'FEATURE_SOURCE';
const REQUIRED_TYPE_CODE = 'DATABASE';
const REQUIRED_DTL_CODE = 'MONGODB';

type ResolvedDataExplorationDataset = {
  datasetKey: string;
  warning: string | null;
};

function normalizeDatasetKey(datasetKey: string | null | undefined): string {
  if (typeof datasetKey !== 'string') {
    return '';
  }
  return datasetKey.trim();
}

function normalizeCompareValue(value: string): string {
  return value.trim().toLowerCase();
}

function normalizePurpose(purpose: string | null | undefined): string {
  if (typeof purpose !== 'string') {
    return '';
  }
  return purpose.trim().toUpperCase();
}

function normalizeTypeCode(typeCode: string | null | undefined): string {
  if (typeof typeCode !== 'string') {
    return '';
  }
  return typeCode.trim().toUpperCase();
}

function isBlockedDatasetKey(datasetKey: string): boolean {
  const normalized = normalizeCompareValue(datasetKey);
  return normalized === LEGACY_GLOBAL_DATASET_KEY || normalized === DEPRECATED_RUNTIME_DATASET_KEY;
}

export function filterDataExplorationDatasetOptions(
  options: DataExplorationDatasetOption[],
): DataExplorationDatasetOption[] {
  const filtered = options.filter((option) => {
    const datasetKey = normalizeDatasetKey(option.datasetKey);
    if (!datasetKey || isBlockedDatasetKey(datasetKey)) {
      return false;
    }
    if (normalizeTypeCode(option.typeCode) !== REQUIRED_TYPE_CODE) {
      return false;
    }
    if (normalizeTypeCode(option.dtlCode) !== REQUIRED_DTL_CODE) {
      return false;
    }
    if (normalizePurpose(option.datasetPurpose) !== REQUIRED_DATASET_PURPOSE) {
      return false;
    }
    return option.featureEnabled === true;
  });

  return filtered
    .slice()
    .sort((left, right) => {
      const leftSort = left.sortNo ?? Number.MAX_SAFE_INTEGER;
      const rightSort = right.sortNo ?? Number.MAX_SAFE_INTEGER;
      if (leftSort !== rightSort) {
        return leftSort - rightSort;
      }
      return normalizeDatasetKey(left.datasetKey).localeCompare(normalizeDatasetKey(right.datasetKey));
    });
}

export function readPersistedDataExplorationDatasetKey(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  try {
    return normalizeDatasetKey(window.localStorage.getItem(DATA_EXPLORATION_DATASET_STORAGE_KEY));
  } catch {
    return '';
  }
}

export function persistDataExplorationDatasetKey(datasetKey: string): void {
  if (typeof window === 'undefined') {
    return;
  }
  const normalized = normalizeDatasetKey(datasetKey);
  if (!normalized) {
    return;
  }
  try {
    window.localStorage.setItem(DATA_EXPLORATION_DATASET_STORAGE_KEY, normalized);
  } catch {
    // Ignore storage errors from private mode or blocked storage.
  }
}

function includesDatasetKey(options: DataExplorationDatasetOption[], datasetKey: string): boolean {
  if (!datasetKey) {
    return false;
  }
  return options.some((option) => normalizeDatasetKey(option.datasetKey) === datasetKey);
}

export function resolveDefaultDataExplorationDatasetKey(
  options: DataExplorationDatasetOption[],
  preferredDatasetKey?: string | null,
): string {
  const preferred = normalizeDatasetKey(preferredDatasetKey ?? '');
  if (includesDatasetKey(options, preferred)) {
    return preferred;
  }

  const persisted = readPersistedDataExplorationDatasetKey();
  if (includesDatasetKey(options, persisted)) {
    return persisted;
  }

  if (includesDatasetKey(options, DEFAULT_DATASET_KEY)) {
    return DEFAULT_DATASET_KEY;
  }

  return normalizeDatasetKey(options[0]?.datasetKey ?? '');
}

export function resolveDataExplorationDatasetKeyWithFallback(options: {
  datasetOptions: DataExplorationDatasetOption[];
  queryDatasetKey?: string | null;
  preferredDatasetKey?: string | null;
  persistedDatasetKey?: string | null;
}): ResolvedDataExplorationDataset {
  const candidates = [
    normalizeDatasetKey(options.queryDatasetKey ?? ''),
    normalizeDatasetKey(options.preferredDatasetKey ?? ''),
    normalizeDatasetKey(options.persistedDatasetKey ?? ''),
    normalizeDatasetKey(readPersistedDataExplorationDatasetKey()),
  ];

  for (const candidate of candidates) {
    if (!candidate) {
      continue;
    }
    if (includesDatasetKey(options.datasetOptions, candidate)) {
      return { datasetKey: candidate, warning: null };
    }
    const fallback = resolveDefaultDataExplorationDatasetKey(options.datasetOptions, options.preferredDatasetKey);
    if (!fallback) {
      return { datasetKey: '', warning: `Requested datasetKey(${candidate}) is not available.` };
    }
    return {
      datasetKey: fallback,
      warning: `Requested datasetKey(${candidate}) is not available. Using ${fallback} instead.`,
    };
  }

  return {
    datasetKey: resolveDefaultDataExplorationDatasetKey(options.datasetOptions, options.preferredDatasetKey),
    warning: null,
  };
}

export function resolveDatasetDisplayName(option: DataExplorationDatasetOption): string {
  return (
    normalizeDatasetKey(option.displayName) ||
    normalizeDatasetKey(option.datasetName) ||
    normalizeDatasetKey(option.datasetKey) ||
    '-'
  );
}

function cloneFilterValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => cloneFilterValue(item));
  }
  if (value && typeof value === 'object') {
    const cloned: Record<string, unknown> = {};
    Object.entries(value as Record<string, unknown>).forEach(([key, nestedValue]) => {
      cloned[key] = cloneFilterValue(nestedValue);
    });
    return cloned;
  }
  return value;
}

export function normalizeAppliedMatchFilter(
  filter: DataExplorationAppliedMatchFilter | null | undefined,
): DataExplorationAppliedMatchFilter {
  if (!filter || typeof filter !== 'object') {
    return {};
  }
  return cloneFilterValue(filter) as DataExplorationAppliedMatchFilter;
}

export function resolveAppliedMatchFilterMccode(
  filter: DataExplorationAppliedMatchFilter | null | undefined,
): string {
  const normalized = normalizeAppliedMatchFilter(filter);
  const direct = normalized.MCCODE ?? normalized.mccode ?? normalized.mcCode;
  if (typeof direct === 'string' && direct.trim().length > 0) {
    return direct.trim().toUpperCase();
  }
  if (direct && typeof direct === 'object') {
    const eqValue = (direct as Record<string, unknown>).$eq;
    if (typeof eqValue === 'string' && eqValue.trim().length > 0) {
      return eqValue.trim().toUpperCase();
    }
  }
  return '';
}

export function formatAppliedMatchFilter(
  filter: DataExplorationAppliedMatchFilter | null | undefined,
): string {
  const normalized = normalizeAppliedMatchFilter(filter);
  const entries = Object.entries(normalized);
  if (entries.length === 0) {
    return '-';
  }
  return entries
    .map(([key, value]) => `${key}=${typeof value === 'string' ? value : JSON.stringify(value)}`)
    .join(', ');
}
