export type HealthIndexRun = {
  runId: string;
  datasetKey: string | null;
  algoCode: string | null;
  algoName: string | null;
  label: string;
  executedAt: string | null;
};

export type HealthIndexPoint = {
  runId: string;
  datasetKey: string;
  equipmentId: string | null;
  windowStart: string | null;
  windowEnd: string | null;
  healthIndex: number | null;
  healthIndexPercent: number | null;
  anomalyScore: number | null;
  status: string | null;
  isAnomaly: boolean | null;
  regDate: string | null;
};

export type HealthIndexSummary = {
  latestHealthIndex: number | null;
  latestHealthIndexPercent: number | null;
  avgHealthIndexPercent: number | null;
  minHealthIndexPercent: number | null;
  maxHealthIndexPercent: number | null;
  latestStatus: string | null;
  normalCount: number;
  warningCount: number;
  criticalCount: number;
  totalCount: number;
  latestWindowStart: string | null;
  latestWindowEnd: string | null;
};

export type HealthIndexTrendResponse = {
  runId: string;
  datasetKey: string;
  summary: HealthIndexSummary;
  points: HealthIndexPoint[];
};

export type FetchHealthIndexTrendParams = {
  runId: string;
  datasetKey: string;
  status?: string | null;
  from?: string | null;
  to?: string | null;
};
