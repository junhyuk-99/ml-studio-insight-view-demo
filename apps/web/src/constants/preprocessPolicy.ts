export type PreprocessOptionStatus = 'recommended' | 'optional' | 'discouraged' | 'disabled';

export type PreprocessPolicyItem = {
  status: PreprocessOptionStatus;
  reason: string;
};

const PREP_TYPE_CODES = [
  'DATA_LOADER',
  'COLUMN_AUTO_CLASSIFY',
  'MISSING_VALUE',
  'OUTLIER',
  'SCALING',
  'CATEGORICAL',
  'TIME_SERIES',
  'FEATURE_ENG',
  'PREVIEW',
] as const;

const BASE_POLICY: Record<string, PreprocessPolicyItem> = {
  DATA_LOADER: { status: 'recommended', reason: '데이터를 불러오고 연결하는 기본 단계입니다.' },
  COLUMN_AUTO_CLASSIFY: { status: 'recommended', reason: '컬럼 의미를 자동 분류해 후속 단계 품질을 높입니다.' },
  MISSING_VALUE: { status: 'optional', reason: '결측이 많은 경우 우선 적용을 검토하세요.' },
  OUTLIER: { status: 'optional', reason: '이상치 비율에 따라 적용 여부를 결정할 수 있습니다.' },
  SCALING: { status: 'optional', reason: '모델 특성에 따라 필요성이 달라집니다.' },
  CATEGORICAL: { status: 'optional', reason: '범주형 입력이 있을 때 유용합니다.' },
  TIME_SERIES: { status: 'discouraged', reason: '시계열 전용 알고리즘이 아니면 일반적으로 비권장입니다.' },
  FEATURE_ENG: { status: 'optional', reason: '도메인 특성에 맞게 추가 파생변수를 구성할 수 있습니다.' },
  PREVIEW: { status: 'recommended', reason: '전처리 적용 전후를 반드시 검토하세요.' },
};

const LINEAR_FAMILY = new Set(['LOGISTIC_REGRESSION', 'SVM', 'MLP', 'ANN']);
const TREE_FAMILY = new Set([
  'RANDOM_FOREST',
  'RANDOM_FOREST_REGRESSOR',
  'XGBOOST',
  'XGBOOST_REGRESSOR',
  'LIGHTGBM',
  'LIGHTGBM_REGRESSOR',
  'ISOLATION_FOREST',
]);
const TIME_SERIES_FAMILY = new Set(['LSTM', 'GRU', 'TCN', 'ARIMA', 'PROPHET', 'LSTM_AE']);
const ANOMALY_FAMILY = new Set(['ONE_CLASS_SVM', 'AUTOENCODER', 'AUTO_ENCODER']);
const CLUSTERING_FAMILY = new Set(['KMEANS', 'DBSCAN', 'HIERARCHICAL', 'HIERARCHICAL_CLUSTERING']);

const FAMILY_OVERRIDES: Record<string, Record<string, PreprocessPolicyItem>> = {
  LINEAR: {
    SCALING: { status: 'recommended', reason: '선형/거리 기반 모델은 스케일링 효과가 큽니다.' },
    CATEGORICAL: { status: 'recommended', reason: '범주형 인코딩 품질이 성능에 직접 영향을 줍니다.' },
    TIME_SERIES: { status: 'disabled', reason: '선택 모델은 시계열 전용 학습 흐름이 아닙니다.' },
  },
  TREE: {
    MISSING_VALUE: { status: 'recommended', reason: '트리 기반 학습에서 결측 처리 전략이 중요합니다.' },
    CATEGORICAL: { status: 'recommended', reason: '범주형 처리 품질이 분할 성능에 영향을 줍니다.' },
    SCALING: { status: 'discouraged', reason: '트리 계열은 스케일링 효과가 제한적입니다.' },
    TIME_SERIES: { status: 'disabled', reason: '일반 트리 알고리즘에는 시계열 처리 단계가 비활성입니다.' },
  },
  TIME_SERIES: {
    TIME_SERIES: { status: 'recommended', reason: '시점/윈도우/래그 기반 전처리가 핵심입니다.' },
    FEATURE_ENG: { status: 'recommended', reason: '시계열 파생 변수(윈도우/주기/시점) 확장이 권장됩니다.' },
    CATEGORICAL: { status: 'discouraged', reason: '시계열에서는 범주형 처리 우선순위가 낮을 수 있습니다.' },
  },
  ANOMALY: {
    MISSING_VALUE: { status: 'recommended', reason: '이상탐지는 결측치 영향이 커서 사전 보정이 권장됩니다.' },
    SCALING: { status: 'recommended', reason: '거리/재구성 오차 기반 모델은 스케일링이 효과적입니다.' },
    TIME_SERIES: { status: 'discouraged', reason: '시계열 특화 이상탐지가 아닌 경우 우선순위가 낮습니다.' },
  },
  CLUSTERING: {
    SCALING: { status: 'recommended', reason: '군집화는 거리 계산 기반이라 스케일링이 중요합니다.' },
    OUTLIER: { status: 'recommended', reason: '군집 경계 왜곡을 줄이기 위해 이상치 처리 권장됩니다.' },
    TIME_SERIES: { status: 'discouraged', reason: '일반 군집화에서는 시계열 처리 필요성이 낮습니다.' },
  },
};

export const PREPROCESS_STATUS_LABEL: Record<PreprocessOptionStatus, string> = {
  recommended: '추천',
  optional: '선택 가능',
  discouraged: '비권장',
  disabled: '비활성',
};

function resolveFamily(algoCd: string | null | undefined) {
  if (!algoCd) {
    return null;
  }
  if (TIME_SERIES_FAMILY.has(algoCd)) {
    return 'TIME_SERIES';
  }
  if (LINEAR_FAMILY.has(algoCd)) {
    return 'LINEAR';
  }
  if (TREE_FAMILY.has(algoCd)) {
    return 'TREE';
  }
  if (ANOMALY_FAMILY.has(algoCd)) {
    return 'ANOMALY';
  }
  if (CLUSTERING_FAMILY.has(algoCd)) {
    return 'CLUSTERING';
  }
  return null;
}

export function getPreprocessPolicyByPrepType(algoCd: string | null | undefined): Record<string, PreprocessPolicyItem> {
  const merged = { ...BASE_POLICY };
  const family = resolveFamily(algoCd);
  if (!family) {
    return merged;
  }

  const override = FAMILY_OVERRIDES[family] ?? {};
  for (const prepTypeCd of PREP_TYPE_CODES) {
    if (override[prepTypeCd]) {
      merged[prepTypeCd] = override[prepTypeCd];
    }
  }
  return merged;
}

