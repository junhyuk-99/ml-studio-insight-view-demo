export type SupervisedRun = {
  runId: string;
  datasetKey: string | null;
  algoCode: string | null;
  algoName: string | null;
  status: string | null;
  triggerType: string | null;
  executedAt: string | null;
  totalPredictions: number;
};

export type SupervisedMetric = {
  key: string;
  label: string;
  value: number | null;
  numerator: number;
  denominator: number;
};

export type SupervisedFeatureImportance = {
  rank: number;
  feature: string;
  importance: number;
};

export type SupervisedSummary = {
  runId: string;
  datasetKey: string | null;
  algoCode: string | null;
  algoName: string | null;
  status: string | null;
  triggerType: string | null;
  executedAt: string | null;
  totalPredictions: number;
  accuracy: SupervisedMetric;
  precision: SupervisedMetric;
  recall: SupervisedMetric;
  f1Score: SupervisedMetric;
  tp: number;
  tn: number;
  fp: number;
  fn: number;
  featureImportances: SupervisedFeatureImportance[];
};

export type SupervisedDistributionItem = {
  errorType: string;
  count: number;
  ratio: number;
};

export type SupervisedDistribution = {
  runId: string;
  totalCount: number;
  tp: number;
  tn: number;
  fp: number;
  fn: number;
  items: SupervisedDistributionItem[];
};

export type SupervisedErrorRow = {
  timestamp: string | null;
  actualLabel: number | null;
  predictionLabel: number | null;
  probabilityAnomaly: number | null;
  probabilityNormal: number | null;
  probability: number | null;
  topFeatures: string[];
};

export type SupervisedErrors = {
  runId: string;
  fpTop: SupervisedErrorRow[];
  fnTop: SupervisedErrorRow[];
};

export type SupervisedPredictionRow = {
  timestamp: string | null;
  actualLabel: number | null;
  predictionLabel: number | null;
  probabilityAnomaly: number | null;
  probabilityNormal: number | null;
  probability: number | null;
  correctYn: string | null;
  errorType: string | null;
  topFeatures: string[];
};

export type SupervisedPredictionPage = {
  items: SupervisedPredictionRow[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
};

export type SupervisedTrendPoint = {
  runId: string;
  regDate: string | null;
  triggerType: string | null;
  accuracy: number | null;
  precision: number | null;
  recall: number | null;
  f1Score: number | null;
};

export type FetchSupervisedRunsParams = {
  triggerType?: 'ALL' | 'MANUAL' | 'SCHEDULE';
  limit?: number;
};

export type FetchSupervisedPredictionsParams = {
  runId: string;
  filter?: 'ALL' | 'CORRECT' | 'INCORRECT' | 'TP' | 'TN' | 'FP' | 'FN';
  from?: string | null;
  to?: string | null;
  page?: number;
  size?: number;
};

export type FetchSupervisedTrendParams = {
  triggerType?: 'ALL' | 'MANUAL' | 'SCHEDULE';
  limit?: number;
};
