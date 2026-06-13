export type AlgoTypeOption = {
  algoTypeCd: string;
  algoTypeNm: string;
  desc: string;
  sortOrd: number | null;
};

export type AlgoOption = {
  algoCd: string;
  algoNm: string;
  sortOrd: number | null;
};

export type AlgorithmActiveSelectionData = {
  dataset_key: string;
  active_policy_id: string | null;
  active_algo_code: string | null;
  active_algo_name: string | null;
  updated_at: string | null;
};

export type AlgorithmSelectionData = {
  algoTypes: AlgoTypeOption[];
  algorithmsByType: Record<string, AlgoOption[]>;
  activeSelection: AlgorithmActiveSelectionData | null;
};

export type AlgorithmSelectionApplyRequest = {
  dataset_key: string;
  algo_code: string;
  changed_by?: string;
  changed_reason?: string;
};

export type AlgorithmSelectionApplyData = {
  dataset_key: string;
  active_policy_id: string | null;
  active_algo_code: string | null;
  active_algo_name: string | null;
  changed_by: string | null;
  changed_reason: string | null;
  use_flag: string | null;
  reg_date: string | null;
  updated_at: string | null;
};

export type AlgorithmComparisonMeta = {
  summary: string;
  accuracy: string;
  trainSpeed: string;
  predictSpeed: string;
  complexity: string;
  interpretability: string;
};
