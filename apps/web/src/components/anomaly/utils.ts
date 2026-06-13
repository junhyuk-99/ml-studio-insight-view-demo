import type { ChipProps } from '@mui/material/Chip';
import type { AnomalyResultPoint } from '../../types/modelTrain';

export function normalizeStatus(status: string | null | undefined): string {
  const normalized = status?.trim().toUpperCase();
  return normalized && normalized.length > 0 ? normalized : 'UNKNOWN';
}

export function statusChipColor(status: string | null | undefined): ChipProps['color'] {
  const normalized = normalizeStatus(status);
  if (normalized === 'NORMAL' || normalized === 'SUCCESS') {
    return 'success';
  }
  if (normalized === 'WARNING' || normalized === 'RUNNING') {
    return 'warning';
  }
  if (normalized === 'CRITICAL' || normalized === 'FAIL') {
    return 'error';
  }
  return 'default';
}

export function statusPointColor(status: string | null | undefined, isAnomaly: boolean | null): string {
  if (isAnomaly) {
    return '#c62828';
  }

  const normalized = normalizeStatus(status);
  if (normalized === 'CRITICAL') {
    return '#c62828';
  }
  if (normalized === 'WARNING') {
    return '#ef6c00';
  }
  if (normalized === 'NORMAL') {
    return '#2e7d32';
  }
  return '#4f6076';
}

export function formatDateTime(value: string | null | undefined): string {
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

export function formatNumber(value: number | null | undefined, digits = 4): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '-';
  }
  return value.toFixed(digits);
}

export function anomalyRowKey(row: AnomalyResultPoint, index: number): string {
  return `${row.run_id}-${row.window_start ?? 'start'}-${row.window_end ?? 'end'}-${index}`;
}
