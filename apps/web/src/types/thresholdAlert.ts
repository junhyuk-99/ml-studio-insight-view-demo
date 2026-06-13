export type ThresholdAlertSummary = {
  totalCount: number;
  openCount: number;
  ackCount: number;
  warningCount: number;
  criticalCount: number;
  latestAlertAt: string | null;
  latestSeverity: string | null;
  latestDisplayName: string | null;
};

export type ThresholdAlertListItem = {
  alertId: string | null;
  ruleId: string | null;
  datasetKey: string | null;
  runId: string | null;
  windowStart: string | null;
  windowEnd: string | null;
  targetType: string | null;
  targetField: string | null;
  displayName: string | null;
  value: number | null;
  severity: string | null;
  operator: string | null;
  warningValue: number | null;
  criticalValue: number | null;
  status: string | null;
  ackYn: string | null;
  ackBy: string | null;
  ackAt: string | null;
  memo: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type ThresholdAlertListResponse = {
  items: ThresholdAlertListItem[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
};

export type ThresholdAlertTrendPoint = {
  alertId: string | null;
  datasetKey: string | null;
  runId: string | null;
  windowStart: string | null;
  windowEnd: string | null;
  createdAt: string | null;
  displayName: string | null;
  targetField: string | null;
  value: number | null;
  valuePercent: number | null;
  severity: string | null;
  warningValue: number | null;
  warningValuePercent: number | null;
  criticalValue: number | null;
  criticalValuePercent: number | null;
  status: string | null;
  ackYn: string | null;
};

export type ThresholdAlertTrendResponse = {
  points: ThresholdAlertTrendPoint[];
  totalReturned: number;
  limit: number;
};

export type ThresholdAlertRecalculateRunResult = {
  runId: string;
  datasetKey: string;
  processedCount: number;
  createdOrUpdatedCount: number;
  skippedCount: number;
  warningCount: number;
  criticalCount: number;
};

export type ThresholdAlertAckRequest = {
  alertId: string;
  ackBy: string;
  memo?: string | null;
};

export type FetchThresholdAlertSummaryParams = {
  datasetKey: string;
  runId?: string | null;
  from?: string | null;
  to?: string | null;
};

export type FetchThresholdAlertListParams = {
  datasetKey: string;
  runId?: string | null;
  severity?: string | null;
  status?: string | null;
  ackYn?: string | null;
  from?: string | null;
  to?: string | null;
  page?: number;
  size?: number;
};

export type FetchThresholdAlertTrendParams = {
  datasetKey: string;
  runId?: string | null;
  severity?: string | null;
  status?: string | null;
  ackYn?: string | null;
  from?: string | null;
  to?: string | null;
  limit?: number;
};

export type ThresholdAlertRecalculateRunRequest = {
  runId: string;
  datasetKey: string;
};
