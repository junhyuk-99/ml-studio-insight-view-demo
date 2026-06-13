export type AlgorithmParamDataType = 'int' | 'float' | 'string' | string;

export type AlgorithmParamItem = {
  algoCd: string;
  paramCd: string;
  paramNm: string;
  dataType: AlgorithmParamDataType;
  requiredYn: 'Y' | 'N' | string;
  defaultValue: string | number | null;
  minValue: string | number | null;
  maxValue: string | number | null;
  uiType: string | null;
  step: string | number | null;
  desc: string | null;
  sortOrd: number | null;
};

export type AlgorithmParamValues = Record<string, string>;

export type AlgorithmParamSaveItem = {
  paramCd: string;
  defaultValue: string | number | null;
  minValue: string | number | null;
  maxValue: string | number | null;
  uiType: string | null;
  step: string | number | null;
};

export type AlgorithmParamSaveRequest = {
  algoCd: string;
  params: AlgorithmParamSaveItem[];
};

export type AlgorithmParamSaveResponse = {
  algoCd: string;
  savedCount: number;
};
