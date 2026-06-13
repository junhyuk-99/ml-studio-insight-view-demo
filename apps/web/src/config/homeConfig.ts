import PolicyRoundedIcon from '@mui/icons-material/PolicyRounded';
import SmartToyRoundedIcon from '@mui/icons-material/SmartToyRounded';
import StorageRoundedIcon from '@mui/icons-material/StorageRounded';
import TaskAltRoundedIcon from '@mui/icons-material/TaskAltRounded';
import UpdateRoundedIcon from '@mui/icons-material/UpdateRounded';
import type { SvgIconComponent } from '@mui/icons-material';
import type { AppRouteId } from './navigationConfig';

export type HomeSummaryCardId =
  | 'raw-data'
  | 'latest-run'
  | 'latest-result'
  | 'active-model'
  | 'latest-update';

export type HomeSummaryCardConfig = {
  id: HomeSummaryCardId;
  title: string;
  description: string;
  routeId: AppRouteId;
  icon: SvgIconComponent;
  accentColor: string;
};

export type HomePipelineStepConfig = {
  step: number;
  title: string;
  description: string;
  routeId: AppRouteId;
  fromCollection: string;
  toCollection: string;
};

export type HomeExplorationFocusConfig = {
  id: string;
  title: string;
  description: string;
  routeId: AppRouteId;
};

export const HOME_SUMMARY_CARD_CONFIG: readonly HomeSummaryCardConfig[] = [
  {
    id: 'raw-data',
    title: '원본 데이터 현황',
    description: 'THISHMIDATA 기준 컬럼 구성',
    routeId: 'operation-preprocess',
    icon: StorageRoundedIcon,
    accentColor: '#1660cf',
  },
  {
    id: 'latest-run',
    title: '최근 분석 실행 상태',
    description: 'thismodelrun 최신 실행 기준',
    routeId: 'operation-modeltrain',
    icon: TaskAltRoundedIcon,
    accentColor: '#0f8c6a',
  },
  {
    id: 'latest-result',
    title: '최근 결과 상태',
    description: 'thisanomalyresult 최신 실행 기준',
    routeId: 'ai-anomaly',
    icon: PolicyRoundedIcon,
    accentColor: '#d15f0e',
  },
  {
    id: 'active-model',
    title: '활성 모델 / 정책',
    description: 'tmst_model_active / tmst_model_policy',
    routeId: 'ai-overview',
    icon: SmartToyRoundedIcon,
    accentColor: '#6a41d7',
  },
  {
    id: 'latest-update',
    title: '최근 업데이트 시각',
    description: '홈 요약 데이터 기준',
    routeId: 'home',
    icon: UpdateRoundedIcon,
    accentColor: '#f06a2a',
  },
] as const;

export const HOME_PIPELINE_STEP_CONFIG: readonly HomePipelineStepConfig[] = [
  {
    step: 1,
    title: '원본 데이터 전처리',
    description: 'Raw 데이터 확인 및 feature 생성 정책 관리',
    routeId: 'operation-preprocess',
    fromCollection: 'THISHMIDATA',
    toCollection: 'thisfeature',
  },
  {
    step: 2,
    title: '알고리즘 선택',
    description: '분석 목적별 알고리즘 선택 및 적용',
    routeId: 'operation-algorithm',
    fromCollection: 'tmst_model_active',
    toCollection: 'tmst_model_policy',
  },
  {
    step: 3,
    title: '모델 분석 / 학습',
    description: '모델 실행 정책 저장 및 실행',
    routeId: 'operation-modeltrain',
    fromCollection: 'thisfeature',
    toCollection: 'thismodelrun',
  },
  {
    step: 4,
    title: '분석 결과 확인',
    description: '이상 탐지 결과와 상세 구간 확인',
    routeId: 'ai-anomaly',
    fromCollection: 'thismodelrun',
    toCollection: 'thisanomalyresult',
  },
] as const;

export const HOME_EXPLORATION_FOCUS_CONFIG: readonly HomeExplorationFocusConfig[] = [
  {
    id: 'distribution',
    title: '분포',
    description: '값 분포와 편향 확인',
    routeId: 'data-exploration-histogram',
  },
  {
    id: 'trend',
    title: '추이',
    description: '시간 흐름 변화 확인',
    routeId: 'data-exploration-timeseries',
  },
  {
    id: 'relation',
    title: '관계',
    description: '상관관계 변화 확인',
    routeId: 'data-exploration-correlation-heatmap',
  },
  {
    id: 'outlier',
    title: '이상치',
    description: '변동성과 이상치 확인',
    routeId: 'data-exploration-boxplot',
  },
] as const;
