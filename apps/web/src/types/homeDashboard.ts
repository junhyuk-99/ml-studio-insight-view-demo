export type HomeDashboardKpi = {
  activeModelCount: number;
  latestRunStatus: string;
  latestRunAt: string | null;
  latestResultStatus: string;
  anomalyCount: number;
  totalResultCount: number;
  datasetCount: number;
  fieldCount: number;
  latestUpdatedAt: string | null;
};

export type HomeDashboardActiveModel = {
  datasetKey: string | null;
  algoCode: string | null;
  algoName: string | null;
  policyId: string | null;
  modelType: string | null;
  status: string | null;
};

export type HomeDashboardAnomalyTrendPoint = {
  date: string;
  count: number;
};

export type HomeDashboardCorrelationSummary = {
  fieldCount: number;
  available: boolean;
  message: string;
};

export type HomeDashboardRecentRun = {
  runId: string;
  runAt: string | null;
  algoCode: string | null;
  algoName: string | null;
  datasetKey: string | null;
  analysisType: string;
  status: string | null;
  resultCount: number;
  anomalyCount: number | null;
  accuracy: number | null;
  f1Score: number | null;
};

export type HomeDashboardLatestSupervised = {
  available: boolean;
  runId: string | null;
  runAt: string | null;
  status: string | null;
  resultCount: number;
  anomalyCount: number;
  accuracy: number | null;
  precision: number | null;
  recall: number | null;
  f1Score: number | null;
  message: string | null;
};

export type HomeDashboardResponse = {
  syncedAt: string | null;
  kpi: HomeDashboardKpi;
  activeModels: HomeDashboardActiveModel[];
  anomalyTrend: HomeDashboardAnomalyTrendPoint[];
  correlationSummary: HomeDashboardCorrelationSummary;
  recentRuns: HomeDashboardRecentRun[];
  latestSupervised: HomeDashboardLatestSupervised;
};
