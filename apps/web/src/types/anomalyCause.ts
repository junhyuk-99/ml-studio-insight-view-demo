export type AnomalyCauseRun = {
  runId: string;
  datasetKey: string | null;
  equipmentId: string | null;
  algoCode: string | null;
  algoName: string | null;
  status: string | null;
  label: string;
  executedAt: string | null;
};

export type AnomalyCauseListItem = {
  id: string;
  runId: string;
  datasetKey: string;
  equipmentId: string | null;
  windowStart: string | null;
  windowEnd: string | null;
  anomalyScore: number | null;
  healthIndex: number | null;
  status: string | null;
  causeGenerated: boolean;
  causeSummary: string[];
  topCauseGroup: string | null;
  topDeviationScore: number | null;
};

export type AnomalyCauseListResponse = {
  items: AnomalyCauseListItem[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
};

export type BaselineScope = {
  datasetKey: string | null;
  statusFilter: string | null;
  from: string | null;
  to: string | null;
  sampleCount: number | null;
};

export type CauseCandidate = {
  rank: number | null;
  feature: string | null;
  sourceField: string | null;
  stat: string | null;
  displayName: string;
  causeGroup: string;
  unit: string | null;
  currentValue: number | null;
  baselineMean: number | null;
  baselineStd: number | null;
  deviationScore: number | null;
  direction: 'HIGH' | 'LOW' | 'FLAT' | 'UNKNOWN' | string;
  reasonText: string | null;
};

export type GroupScore = {
  causeGroup: string;
  score: number | null;
  topFeature: string | null;
  rank: number | null;
};

export type AnomalyCauseDetail = {
  runId: string;
  datasetKey: string;
  equipmentId: string | null;
  windowStart: string | null;
  windowEnd: string | null;
  anomalyScore: number | null;
  healthIndex: number | null;
  anomalyStatus: string | null;
  causeMethod: string | null;
  causeVersion: string | null;
  baselineScope: BaselineScope | null;
  causeSummary: string[];
  causeCandidates: CauseCandidate[];
  groupScores: GroupScore[];
  sourceRef: Record<string, unknown>;
  causeGenerated: boolean;
  createdAt: string | null;
  updatedAt: string | null;
};

export type FetchAnomalyCauseListParams = {
  runId: string;
  datasetKey: string;
  equipmentId: string;
  status?: string | null;
  from?: string | null;
  to?: string | null;
  page?: number;
  size?: number;
};

export type FetchAnomalyCauseDetailParams = {
  runId: string;
  datasetKey: string;
  equipmentId: string;
  windowStart: string;
  windowEnd: string;
};

export type RecalculateAnomalyCauseRequest = {
  runId: string;
  datasetKey: string;
  equipmentId: string;
  windowStart: string;
  windowEnd: string;
};

export type RecalculateAnomalyCauseRunRequest = {
  runId: string;
  datasetKey: string;
  equipmentId: string;
};

export type RecalculateAnomalyCauseRunResult = {
  runId: string;
  datasetKey: string;
  equipmentId: string | null;
  processedCount: number;
  createdOrUpdatedCount: number;
  skippedCount: number;
};
