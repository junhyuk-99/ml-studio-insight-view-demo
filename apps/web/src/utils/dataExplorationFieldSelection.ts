import type { HistogramFieldOption } from '../types/dataExploration';

const EXCLUDED_ANALYSIS_FIELDS = [
  '_id',
  'PRDTIME',
  'timestamp',
  'MCCODE',
  'equipment_id',
  'SOURCE_TYPE_CODE',
  'SOURCE_DTL_CODE',
  'SOURCE_FILE',
  'reg_date',
  'REG_DATE',
];

const STATUS_CATEGORY_FIELDS = [
  'ANAL_STAT',
  'OPSTAT',
  'HEATER_STAT',
  'OPALARM',
  'SEGMENT_NO',
  'SEGMENT_TIME',
  'SEGMENT_TOTAL',
  'PAT_PGM',
];

const EXCLUDED_ANALYSIS_FIELDS_LOWER = new Set(
  EXCLUDED_ANALYSIS_FIELDS.map((fieldName) => fieldName.toLowerCase()),
);
const STATUS_CATEGORY_FIELDS_LOWER = new Set(
  STATUS_CATEGORY_FIELDS.map((fieldName) => fieldName.toLowerCase()),
);

const PV_SUFFIX = '_PV';
const SV_SUFFIX = '_SV';

function normalizeFieldName(fieldName: string | null | undefined): string | null {
  if (typeof fieldName !== 'string') {
    return null;
  }
  const trimmed = fieldName.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function resolveFieldName(field: HistogramFieldOption): string | null {
  return normalizeFieldName(field.field) ?? normalizeFieldName(field.field_name);
}

function isExcludedAnalysisField(fieldName: string): boolean {
  return EXCLUDED_ANALYSIS_FIELDS_LOWER.has(fieldName.toLowerCase());
}

function isPvField(fieldName: string): boolean {
  return fieldName.toUpperCase().endsWith(PV_SUFFIX);
}

function isSvField(fieldName: string): boolean {
  return fieldName.toUpperCase().endsWith(SV_SUFFIX);
}

export function isProcessValueField(fieldName: string | null | undefined): boolean {
  const normalizedFieldName = normalizeFieldName(fieldName);
  if (!normalizedFieldName) {
    return false;
  }
  return isPvField(normalizedFieldName) || isSvField(normalizedFieldName);
}

export function isStatusCategoryField(fieldName: string | null | undefined): boolean {
  const normalizedFieldName = normalizeFieldName(fieldName);
  if (!normalizedFieldName) {
    return false;
  }
  const lowered = normalizedFieldName.toLowerCase();
  const upper = normalizedFieldName.toUpperCase();
  return (
    STATUS_CATEGORY_FIELDS_LOWER.has(lowered) ||
    upper.startsWith('SEGMENT_') ||
    upper.endsWith('_STAT') ||
    upper.includes('OPALARM') ||
    upper.includes('OPSTAT')
  );
}

export function hasExplorationFieldValue(field: HistogramFieldOption): boolean {
  if (typeof field.has_value === 'boolean') {
    return field.has_value;
  }
  if (typeof field.non_null_count === 'number') {
    return field.non_null_count > 0;
  }
  return true;
}

export function isExplorationFieldDisabled(field: HistogramFieldOption): boolean {
  const fieldName = resolveFieldName(field);
  if (!fieldName || isExcludedAnalysisField(fieldName)) {
    return true;
  }
  return !hasExplorationFieldValue(field);
}

function resolveFieldPriority(fieldName: string): number {
  if (isPvField(fieldName)) {
    return 0;
  }
  if (isSvField(fieldName)) {
    return 1;
  }
  if (isStatusCategoryField(fieldName)) {
    return 3;
  }
  return 2;
}

export function sortExplorationFields(fieldOptions: HistogramFieldOption[]): HistogramFieldOption[] {
  const indexedFields = fieldOptions.map((field, index) => ({ field, index }));
  indexedFields.sort((left, right) => {
    const leftFieldName = resolveFieldName(left.field);
    const rightFieldName = resolveFieldName(right.field);
    if (!leftFieldName && !rightFieldName) {
      return left.index - right.index;
    }
    if (!leftFieldName) {
      return 1;
    }
    if (!rightFieldName) {
      return -1;
    }
    const priorityDiff = resolveFieldPriority(leftFieldName) - resolveFieldPriority(rightFieldName);
    if (priorityDiff !== 0) {
      return priorityDiff;
    }
    return left.index - right.index;
  });
  return indexedFields.map((entry) => entry.field);
}

export function normalizeExplorationFieldOptions(fieldOptions: HistogramFieldOption[]): HistogramFieldOption[] {
  const deduplicated = new Map<string, HistogramFieldOption>();

  for (const fieldOption of fieldOptions) {
    const fieldName = resolveFieldName(fieldOption);
    if (!fieldName || isExcludedAnalysisField(fieldName)) {
      continue;
    }
    const dedupeKey = fieldName.toLowerCase();
    if (deduplicated.has(dedupeKey)) {
      continue;
    }
    deduplicated.set(dedupeKey, {
      ...fieldOption,
      field: fieldName,
      field_name: fieldOption.field_name ?? fieldName,
    });
  }

  return sortExplorationFields(Array.from(deduplicated.values()));
}

export function isDefaultExplorationField(
  fieldName: string | null | undefined,
  fieldMeta?: HistogramFieldOption,
): boolean {
  const normalizedFieldName = normalizeFieldName(fieldName);
  if (!normalizedFieldName) {
    return false;
  }
  if (!isProcessValueField(normalizedFieldName)) {
    return false;
  }
  if (isStatusCategoryField(normalizedFieldName)) {
    return false;
  }
  if (fieldMeta && isExplorationFieldDisabled(fieldMeta)) {
    return false;
  }
  return true;
}

function applyResponseDefaultOrder(
  candidateFieldNames: string[],
  responseDefaultSelectedFields: string[] | null | undefined,
): string[] {
  if (!candidateFieldNames.length) {
    return [];
  }

  const candidateNameSet = new Set(candidateFieldNames);
  const orderedDefaults: string[] = [];

  for (const rawFieldName of responseDefaultSelectedFields ?? []) {
    const normalizedFieldName = normalizeFieldName(rawFieldName);
    if (!normalizedFieldName || !candidateNameSet.has(normalizedFieldName)) {
      continue;
    }
    if (orderedDefaults.includes(normalizedFieldName)) {
      continue;
    }
    orderedDefaults.push(normalizedFieldName);
  }

  if (orderedDefaults.length === 0) {
    return candidateFieldNames;
  }

  return [
    ...orderedDefaults,
    ...candidateFieldNames.filter((fieldName) => !orderedDefaults.includes(fieldName)),
  ];
}

export function buildInitialSelectedFields(
  fieldOptions: HistogramFieldOption[],
  responseDefaultSelectedFields: string[] | null | undefined,
  maxSelectedCount: number,
): string[] {
  const normalizedFieldOptions = normalizeExplorationFieldOptions(fieldOptions);
  const selectableFields = normalizedFieldOptions.filter(
    (fieldOption) => !isExplorationFieldDisabled(fieldOption),
  );

  if (selectableFields.length === 0) {
    return [];
  }

  const preferredProcessFieldNames = selectableFields
    .filter((fieldOption) => isDefaultExplorationField(fieldOption.field, fieldOption))
    .map((fieldOption) => fieldOption.field);

  const nonStatusFieldNames = selectableFields
    .filter((fieldOption) => !isStatusCategoryField(fieldOption.field))
    .map((fieldOption) => fieldOption.field);

  const fallbackFieldNames = selectableFields.map((fieldOption) => fieldOption.field);

  const orderedCandidates = preferredProcessFieldNames.length
    ? applyResponseDefaultOrder(preferredProcessFieldNames, responseDefaultSelectedFields)
    : nonStatusFieldNames.length
      ? applyResponseDefaultOrder(nonStatusFieldNames, responseDefaultSelectedFields)
      : applyResponseDefaultOrder(fallbackFieldNames, responseDefaultSelectedFields);

  if (!Number.isFinite(maxSelectedCount) || maxSelectedCount <= 0) {
    return orderedCandidates;
  }

  return orderedCandidates.slice(0, Math.floor(maxSelectedCount));
}
