import type { DataExplorationDatasetOption } from '../types/dataExploration';

const OPERATION_DATASET_STORAGE_KEY = 'operation.selectedDatasetKey';
const LEGACY_GLOBAL_DATASET_KEY = 'demo_hmi_all_default_v1';
const DEPRECATED_RUNTIME_DATASET_KEY = 'thisraw_all_default_v1';
const DATASET_POLICY_SUFFIX = '_default_v1';

export type OperationDatasetOption = DataExplorationDatasetOption;

type DatasetCandidate = {
  sourceLabel: string;
  value: string | null | undefined;
};

type ResolvedOperationDataset = {
  datasetKey: string;
  warning: string | null;
};

function normalizeDatasetKey(datasetKey: unknown): string {
  if (typeof datasetKey !== 'string') {
    return '';
  }
  return datasetKey.trim();
}

function normalizePurpose(purpose: string | null | undefined): string {
  if (typeof purpose !== 'string') {
    return '';
  }
  return purpose.trim().toUpperCase();
}

function normalizeCompareValue(value: string): string {
  return value.trim().toLowerCase();
}

function isBlockedDatasetKey(datasetKey: string): boolean {
  const normalized = normalizeCompareValue(datasetKey);
  return normalized === LEGACY_GLOBAL_DATASET_KEY || normalized === DEPRECATED_RUNTIME_DATASET_KEY;
}

function toDatasetKeyLookup(options: OperationDatasetOption[]): Map<string, string> {
  const lookup = new Map<string, string>();
  options.forEach((option) => {
    const normalized = normalizeDatasetKey(option.datasetKey);
    if (!normalized) {
      return;
    }
    lookup.set(normalizeCompareValue(normalized), normalized);
  });
  return lookup;
}

function resolveDatasetKeyByLookup(lookup: Map<string, string>, candidate: string | null | undefined): string {
  const normalizedCandidate = normalizeDatasetKey(candidate);
  if (!normalizedCandidate) {
    return '';
  }

  const compareCandidate = normalizeCompareValue(normalizedCandidate);
  const directMatch = lookup.get(compareCandidate);
  if (directMatch) {
    return directMatch;
  }

  if (!compareCandidate.endsWith(DATASET_POLICY_SUFFIX)) {
    const withSuffix = `${compareCandidate}${DATASET_POLICY_SUFFIX}`;
    const suffixMatch = lookup.get(withSuffix);
    if (suffixMatch) {
      return suffixMatch;
    }
  }

  if (compareCandidate.endsWith(DATASET_POLICY_SUFFIX)) {
    const withoutSuffix = compareCandidate.slice(0, -DATASET_POLICY_SUFFIX.length);
    const noSuffixMatch = lookup.get(withoutSuffix);
    if (noSuffixMatch) {
      return noSuffixMatch;
    }
  }

  return '';
}

export function readPersistedOperationDatasetKey(): string {
  if (typeof window === 'undefined') {
    return '';
  }

  try {
    return normalizeDatasetKey(window.localStorage.getItem(OPERATION_DATASET_STORAGE_KEY));
  } catch {
    return '';
  }
}

export function persistOperationDatasetKey(datasetKey: string): void {
  if (typeof window === 'undefined') {
    return;
  }

  const normalized = normalizeDatasetKey(datasetKey);
  if (!normalized) {
    return;
  }

  try {
    window.localStorage.setItem(OPERATION_DATASET_STORAGE_KEY, normalized);
  } catch {
    // Ignore storage errors.
  }
}

export function filterOperationDatasetOptions(options: OperationDatasetOption[]): OperationDatasetOption[] {
  const filtered = options.filter((option) => {
    const datasetKey = normalizeDatasetKey(option.datasetKey);
    if (!datasetKey || isBlockedDatasetKey(datasetKey)) {
      return false;
    }

    if (normalizePurpose(option.datasetPurpose) !== 'FEATURE_SOURCE') {
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

export function resolveOperationDatasetLabel(option: OperationDatasetOption | null | undefined): string {
  if (!option) {
    return '-';
  }

  const displayName = normalizeDatasetKey(option.displayName);
  if (displayName) {
    return displayName;
  }

  const datasetName = normalizeDatasetKey(option.datasetName);
  if (datasetName) {
    return datasetName;
  }

  const datasetKey = normalizeDatasetKey(option.datasetKey);
  return datasetKey || '-';
}

export function extractDatasetKeyFromRouteState(state: unknown): string {
  if (!state || typeof state !== 'object') {
    return '';
  }

  const record = state as Record<string, unknown>;

  const directCandidates = [record.datasetKey, record.dataset_key, record.selectedDataset];
  for (const candidate of directCandidates) {
    if (typeof candidate === 'string') {
      const normalized = normalizeDatasetKey(candidate);
      if (normalized) {
        return normalized;
      }
    }
  }

  const selectedDataset = record.selectedDataset;
  if (selectedDataset && typeof selectedDataset === 'object') {
    const selectedDatasetRecord = selectedDataset as Record<string, unknown>;
    const nestedCandidates = [selectedDatasetRecord.datasetKey, selectedDatasetRecord.dataset_key];
    for (const candidate of nestedCandidates) {
      if (typeof candidate === 'string') {
        const normalized = normalizeDatasetKey(candidate);
        if (normalized) {
          return normalized;
        }
      }
    }
  }

  return '';
}

export function resolveOperationDatasetKeyWithFallback(options: {
  datasetOptions: OperationDatasetOption[];
  queryDatasetKey?: string | null;
  stateDatasetKey?: string | null;
  persistedDatasetKey?: string | null;
  defaultDatasetKey?: string | null;
}): ResolvedOperationDataset {
  const lookup = toDatasetKeyLookup(options.datasetOptions);
  const candidates: DatasetCandidate[] = [
    { sourceLabel: 'query string datasetKey', value: options.queryDatasetKey },
    { sourceLabel: 'router state datasetKey', value: options.stateDatasetKey },
    { sourceLabel: 'localStorage datasetKey', value: options.persistedDatasetKey },
  ];

  for (const candidate of candidates) {
    const matched = resolveDatasetKeyByLookup(lookup, candidate.value);
    if (matched) {
      return { datasetKey: matched, warning: null };
    }

    const normalizedCandidate = normalizeDatasetKey(candidate.value);
    if (normalizedCandidate) {
      const fallback = options.datasetOptions[0]?.datasetKey ?? normalizeDatasetKey(options.defaultDatasetKey);
      const warning = fallback
        ? `요청한 datasetKey(${normalizedCandidate})는 운영 대상이 아니어서 ${fallback}로 전환합니다.`
        : `요청한 datasetKey(${normalizedCandidate})는 운영 대상이 아닙니다.`;
      return {
        datasetKey: fallback,
        warning,
      };
    }
  }

  const serverFirst = options.datasetOptions[0]?.datasetKey;
  if (serverFirst) {
    return { datasetKey: serverFirst, warning: null };
  }

  const defaultMatched = resolveDatasetKeyByLookup(lookup, options.defaultDatasetKey);
  if (defaultMatched) {
    return { datasetKey: defaultMatched, warning: null };
  }

  return {
    datasetKey: normalizeDatasetKey(options.defaultDatasetKey),
    warning: null,
  };
}

export function resolveEquipmentIdByDatasetKey(datasetKey: string | null | undefined): string {
  const normalized = normalizeDatasetKey(datasetKey).toLowerCase();
  const matched = normalized.match(/_demo_mc_(\d{3})_/);
  if (!matched) {
    return '';
  }
  return `DEMO-MC-${matched[1]}`;
}