export type AlgorithmVisualMeta = {
  imageSrc: string;
  imageAlt: string;
  overview: string;
  detail: string;
  strengths: string[];
  recommendedFor: string[];
};

const DEFAULT_VISUAL_META: AlgorithmVisualMeta = {
  imageSrc: '/images/algorithms/default.png',
  imageAlt: '알고리즘 설명 이미지',
  overview: '선택한 알고리즘의 핵심 개념을 설명하는 이미지입니다.',
  detail: '데이터 특성과 목적에 따라 적합한 알고리즘을 선택할 수 있습니다.',
  strengths: ['핵심 구조 이해', '알고리즘 비교 가능'],
  recommendedFor: ['알고리즘 개념 이해가 필요한 경우'],
};

const ALGORITHM_VISUAL_META: Record<string, AlgorithmVisualMeta> = {
  ANN: {
    imageSrc: '/ANN.png',
    imageAlt: 'ANN 알고리즘 설명 이미지',
    overview: '입력층부터 출력층까지 연결된 인공신경망 구조를 통해 패턴을 학습하는 모델입니다.',
    detail:
      '여러 노드와 가중치가 비선형 관계를 학습하며, 복잡한 입력 데이터에서도 특징을 추출할 수 있습니다.',
    strengths: ['비선형 패턴 학습', '복잡한 입력 처리'],
    recommendedFor: ['복잡한 패턴 인식', '다양한 입력 특성 학습'],
  },
  AUTOENCODER: {
    imageSrc: '/AutoEncoder.png',
    imageAlt: 'AutoEncoder 알고리즘 설명 이미지',
    overview: '입력 데이터를 압축한 뒤 다시 복원하면서 핵심 특징을 학습하는 모델입니다.',
    detail:
        '정상 패턴을 잘 복원하도록 학습한 뒤, 복원 오차가 큰 데이터를 이상으로 판단하는 데 자주 사용됩니다.',
    strengths: ['특징 압축', '재구성 오차 기반 탐지'],
    recommendedFor: ['이상탐지', '차원 축소', '비지도 특징 학습'],
    },
  GRU: {
    imageSrc: '/GRU.png',
    imageAlt: 'GRU 알고리즘 설명 이미지',
    overview: '게이트 구조를 사용해 시계열 정보의 흐름을 효율적으로 학습하는 순환신경망입니다.',
    detail:
      'LSTM보다 구조가 단순해 학습이 가벼우면서도 시계열의 문맥 정보를 비교적 잘 유지합니다.',
    strengths: ['경량 시계열 학습', '게이트 기반 정보 유지'],
    recommendedFor: ['빠른 시계열 학습', 'LSTM 대체 모델'],
  },
  ISOLATION_FOREST: {
    imageSrc: '/Isolation Forest.png',
    imageAlt: 'Isolation Forest 알고리즘 설명 이미지',
    overview: '데이터를 반복적으로 분할해 이상치를 빠르게 고립시키는 이상탐지 알고리즘입니다.',
    detail:
      '이상 데이터는 정상 데이터보다 적은 분할만으로 분리되기 때문에 짧은 경로 길이를 갖는 특징이 있습니다.',
    strengths: ['빠른 이상탐지', '대용량 데이터에 유리'],
    recommendedFor: ['비지도 이상탐지', '센서 이상 패턴 탐지'],
  },
  LIGHTGBM: {
    imageSrc: '/LightGBM.jpg',
    imageAlt: 'LightGBM 알고리즘 설명 이미지',
    overview: '리프 중심 분기와 히스토그램 기반 분할을 활용하는 고속 부스팅 모델입니다.',
    detail:
      '학습 속도가 빠르고 대규모 데이터에서도 효율적이며, 성능과 속도의 균형이 좋은 편입니다.',
    strengths: ['빠른 학습', '고성능 부스팅'],
    recommendedFor: ['고속 분류', '대용량 학습 데이터'],
  },
  LIGHTGBM_REGRESSOR: {
    imageSrc: '/LightGBM Regressor.jpg',
    imageAlt: 'LightGBM Regressor 알고리즘 설명 이미지',
    overview: 'LightGBM 구조를 회귀 문제에 적용해 연속형 값을 빠르게 예측하는 모델입니다.',
    detail:
      '여러 부스팅 트리를 순차적으로 학습해 오차를 줄이며, 회귀 성능과 학습 효율을 동시에 노릴 수 있습니다.',
    strengths: ['빠른 회귀 학습', '높은 예측 성능'],
    recommendedFor: ['연속값 예측', '대용량 회귀 데이터'],
  },
  LINEAR_REGRESSION: {
    imageSrc: '/Linear Regression.jpg',
    imageAlt: 'Linear Regression 알고리즘 설명 이미지',
    overview: '입력 변수와 목표값 사이의 선형 관계를 직선으로 표현하는 가장 기본적인 회귀 모델입니다.',
    detail:
      '해석이 쉽고 학습이 빠르며, 변수 변화가 결과에 어떤 영향을 주는지 설명하기 좋습니다.',
    strengths: ['높은 해석성', '매우 빠른 학습'],
    recommendedFor: ['기초 회귀 분석', '설명 가능한 예측'],
  },
  LOGISTIC_REGRESSION: {
    imageSrc: '/Logistic Regression.jpg',
    imageAlt: 'Logistic Regression 알고리즘 설명 이미지',
    overview: '확률값을 기반으로 클래스를 구분하는 선형 분류 모델입니다.',
    detail:
      '시그모이드 함수를 사용해 특정 클래스에 속할 확률을 계산하고, 임계값을 기준으로 분류합니다.',
    strengths: ['해석 용이', '빠른 학습과 추론'],
    recommendedFor: ['이진 분류', '설명 가능한 분류'],
  },
  LSTM: {
    imageSrc: '/LSTM.png',
    imageAlt: 'LSTM 알고리즘 설명 이미지',
    overview: '장기 의존성을 기억하도록 설계된 대표적인 시계열 순환신경망입니다.',
    detail:
      '입력 게이트, 망각 게이트, 출력 게이트를 통해 긴 시퀀스에서도 중요한 정보를 유지합니다.',
    strengths: ['장기 패턴 기억', '시계열 예측에 강함'],
    recommendedFor: ['시계열 예측', '순차 데이터 분석'],
  },
  LSTM_AE: {
    imageSrc: '/LSTM-AE.png',
    imageAlt: 'LSTM-AE 알고리즘 설명 이미지',
    overview: 'LSTM 기반 인코더-디코더 구조로 시계열을 압축하고 복원하는 오토인코더입니다.',
    detail:
      '정상 시계열 패턴을 복원하도록 학습한 뒤, 복원 오차를 기준으로 이상 여부를 판단합니다.',
    strengths: ['시계열 이상탐지', '순차 패턴 재구성'],
    recommendedFor: ['설비 이상징후 탐지', '센서 시계열 이상탐지'],
  },
  MLP: {
    imageSrc: '/MLP.jpg',
    imageAlt: 'MLP 알고리즘 설명 이미지',
    overview: '여러 은닉층을 가진 다층 퍼셉트론으로 비선형 패턴을 학습하는 대표적인 신경망입니다.',
    detail:
      '입력 특성 간 복잡한 관계를 학습할 수 있어 분류와 회귀에 모두 활용할 수 있습니다.',
    strengths: ['비선형 학습', '범용성 높음'],
    recommendedFor: ['일반 분류', '일반 회귀', '복잡한 패턴 데이터'],
  },
  ONE_CLASS_SVM: {
    imageSrc: '/One-Class SVM.png',
    imageAlt: 'One-Class SVM 알고리즘 설명 이미지',
    overview: '정상 데이터의 경계를 학습한 뒤 그 바깥 영역을 이상으로 판단하는 모델입니다.',
    detail:
      '정상 패턴이 명확한 경우에 적합하며, 새로운 데이터가 학습된 경계를 벗어나면 이상으로 볼 수 있습니다.',
    strengths: ['정상 패턴 기반 이상탐지', '경계 학습'],
    recommendedFor: ['정상 데이터 위주 학습', '비지도 이상탐지'],
  },
  RANDOM_FOREST: {
    imageSrc: '/Random Forest.jpg',
    imageAlt: 'Random Forest 알고리즘 설명 이미지',
    overview: '여러 개의 결정트리를 결합해 안정적인 예측을 수행하는 앙상블 모델입니다.',
    detail:
      '각기 다른 샘플과 특성으로 여러 트리를 만든 뒤 결과를 종합하기 때문에 과적합을 줄이고 안정성을 높입니다.',
    strengths: ['안정적인 성능', '과적합 완화'],
    recommendedFor: ['일반 분류 문제', '기본 추천 모델'],
  },
  RANDOM_FOREST_REGRESSOR: {
    imageSrc: '/Random Forest Regressor.jpg',
    imageAlt: 'Random Forest Regressor 알고리즘 설명 이미지',
    overview: '여러 회귀 트리의 결과를 평균내어 연속형 값을 예측하는 앙상블 회귀 모델입니다.',
    detail:
      '단일 트리보다 안정적이며, 비선형 관계가 있는 회귀 문제에서도 무난하게 사용할 수 있습니다.',
    strengths: ['안정적인 회귀 성능', '비선형 관계 대응'],
    recommendedFor: ['연속값 예측', '기본 회귀 모델'],
  },
  SVM: {
    imageSrc: '/SVM.jpg',
    imageAlt: 'SVM 알고리즘 설명 이미지',
    overview: '서로 다른 클래스를 가장 넓은 마진으로 구분하는 결정 경계를 찾는 모델입니다.',
    detail:
      '경계와 가까운 데이터인 서포트 벡터를 중심으로 최적의 초평면을 계산해 분류 성능을 확보합니다.',
    strengths: ['명확한 경계 학습', '고차원 데이터 대응'],
    recommendedFor: ['소규모 고차원 분류', '경계 중심 분류'],
  },
  TCN: {
    imageSrc: '/TCN.png',
    imageAlt: 'TCN 알고리즘 설명 이미지',
    overview: '합성곱 구조를 이용해 시계열의 패턴을 학습하는 시계열 전용 신경망입니다.',
    detail:
      'Dilated convolution을 통해 긴 구간의 정보를 효율적으로 반영하며 병렬 처리에도 유리합니다.',
    strengths: ['병렬 처리 가능', '긴 시계열 수용'],
    recommendedFor: ['시계열 예측', '빠른 시계열 모델'],
  },
  XGBOOST: {
    imageSrc: '/XGBoost.jpg',
    imageAlt: 'XGBoost 알고리즘 설명 이미지',
    overview: '이전 단계의 오차를 반복적으로 보완하며 성능을 높이는 부스팅 모델입니다.',
    detail:
      '잔차를 줄이는 방향으로 트리를 순차적으로 추가해 높은 성능을 내며, 규제와 최적화 기법도 잘 갖춰져 있습니다.',
    strengths: ['매우 높은 성능', '정교한 부스팅'],
    recommendedFor: ['고성능 분류', '성능 우선 모델'],
  },
  XGBOOST_REGRESSOR: {
    imageSrc: '/XGBoost Regressor.jpg',
    imageAlt: 'XGBoost Regressor 알고리즘 설명 이미지',
    overview: 'XGBoost를 회귀 문제에 적용해 오차를 순차적으로 줄이며 값을 예측하는 모델입니다.',
    detail:
      '잔차 기반 학습을 통해 예측 성능을 끌어올리며, 복잡한 회귀 문제에서도 강한 성능을 보이는 편입니다.',
    strengths: ['고성능 회귀', '잔차 기반 보정'],
    recommendedFor: ['성능 중심 회귀', '복잡한 연속값 예측'],
  },
  ARIMA: {
    imageSrc: '/ARIMA.png',
    imageAlt: 'ARIMA 알고리즘 설명 이미지',
    overview: '자기회귀와 차분, 이동평균을 결합해 시계열을 예측하는 통계 기반 모델입니다.',
    detail:
      '과거 값과 오차의 패턴을 이용해 미래를 예측하며, 비교적 해석이 쉬운 전통적인 시계열 모델입니다.',
    strengths: ['통계 기반 해석 가능', '기본 시계열 예측'],
    recommendedFor: ['기초 시계열 예측', '추세 기반 분석'],
  },
  PROPHET: {
    imageSrc: '/Prophet.png',
    imageAlt: 'Prophet 알고리즘 설명 이미지',
    overview: '추세, 계절성, 휴일 효과를 분리해 다루는 시계열 예측 모델입니다.',
    detail:
      '비즈니스형 시계열에 강하며 파라미터 조정이 비교적 쉬워 빠르게 예측 모델을 구축하기 좋습니다.',
    strengths: ['계절성 반영', '빠른 구축'],
    recommendedFor: ['수요 예측', '트렌드/계절성 분석'],
  },
  'KMEANS': {
    imageSrc: '/K-Means.png',
    imageAlt: 'K-Means 알고리즘 설명 이미지',
    overview: '데이터를 중심점과의 거리 기준으로 여러 군집으로 나누는 대표적인 군집화 알고리즘입니다.',
    detail:
      '각 군집의 중심점을 반복적으로 갱신하면서 군집 내 거리를 줄이는 방향으로 학습합니다.',
    strengths: ['단순하고 빠름', '군집 구조 파악 용이'],
    recommendedFor: ['기본 군집화', '패턴 그룹 분류'],
  },
  DBSCAN: {
    imageSrc: '/DBSCAN.png',
    imageAlt: 'DBSCAN 알고리즘 설명 이미지',
    overview: '밀도가 높은 영역을 하나의 군집으로 묶는 밀도 기반 군집화 알고리즘입니다.',
    detail:
      '군집 개수를 미리 정하지 않아도 되고, 노이즈와 이상치를 함께 구분할 수 있는 장점이 있습니다.',
    strengths: ['노이즈 탐지 가능', '복잡한 군집 형태 대응'],
    recommendedFor: ['밀도 기반 군집화', '이상 포함 데이터'],
  },
  HIERARCHICAL: {
  imageSrc: '/HierarchicalClustering.png',
  imageAlt: 'Hierarchical Clustering 알고리즘 설명 이미지',
  overview: '데이터 간 유사도를 바탕으로 계층 구조의 군집을 단계적으로 만드는 알고리즘입니다.',
  detail:
    '덴드로그램을 통해 군집 형성 과정을 확인할 수 있어 군집 관계를 시각적으로 이해하기 좋습니다.',
  strengths: ['계층 구조 확인', '해석 용이'],
  recommendedFor: ['군집 관계 분석', '시각적 군집 해석'],
},
};

export function getAlgorithmVisualMeta(algoCd?: string | null): AlgorithmVisualMeta {
  if (!algoCd) {
    return DEFAULT_VISUAL_META;
  }
  return ALGORITHM_VISUAL_META[algoCd] ?? DEFAULT_VISUAL_META;
}