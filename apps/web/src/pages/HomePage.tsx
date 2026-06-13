import AccountTreeRoundedIcon from '@mui/icons-material/AccountTreeRounded';
import DataObjectRoundedIcon from '@mui/icons-material/DataObjectRounded';
import MonitorHeartRoundedIcon from '@mui/icons-material/MonitorHeartRounded';
import ModelTrainingRoundedIcon from '@mui/icons-material/ModelTrainingRounded';
import PolicyRoundedIcon from '@mui/icons-material/PolicyRounded';
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import SettingsSuggestRoundedIcon from '@mui/icons-material/SettingsSuggestRounded';
import SmartToyRoundedIcon from '@mui/icons-material/SmartToyRounded';
import StorageRoundedIcon from '@mui/icons-material/StorageRounded';
import TaskAltRoundedIcon from '@mui/icons-material/TaskAltRounded';
import UpdateRoundedIcon from '@mui/icons-material/UpdateRounded';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import type { ElementType } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router-dom';
import { getRoutePathById, extractAccessiblePaths, type AppRouteId } from '../config/navigationConfig';
import type { MainLayoutOutletContext } from '../layout/mainLayoutContext';
import { homeService } from '../services/homeService';
import type {
  HomeDashboardActiveModel,
  HomeDashboardAnomalyTrendPoint,
  HomeDashboardRecentRun,
  HomeDashboardResponse,
} from '../types/homeDashboard';

const EMPTY_DASHBOARD: HomeDashboardResponse = {
  syncedAt: null,
  kpi: {
    activeModelCount: 0,
    latestRunStatus: 'NO_DATA',
    latestRunAt: null,
    latestResultStatus: 'NO_DATA',
    anomalyCount: 0,
    totalResultCount: 0,
    datasetCount: 0,
    fieldCount: 0,
    latestUpdatedAt: null,
  },
  activeModels: [],
  anomalyTrend: [],
  correlationSummary: {
    fieldCount: 0,
    available: false,
    message: '상관관계를 계산할 데이터가 없습니다.',
  },
  recentRuns: [],
  latestSupervised: {
    available: false,
    runId: null,
    runAt: null,
    status: 'NO_DATA',
    resultCount: 0,
    anomalyCount: 0,
    accuracy: null,
    precision: null,
    recall: null,
    f1Score: null,
    message: '지도학습 결과 데이터가 없습니다.',
  },
};

type StatusTone = {
  color: string;
  backgroundColor: string;
  borderColor: string;
};

type KpiCardConfig = {
  id: string;
  title: string;
  description: string;
  icon: ElementType;
  value: string;
  subValue: string;
  statusCode?: string | null;
};

type QuickMenuConfig = {
  id: string;
  title: string;
  description: string;
  routeId: AppRouteId;
  icon: ElementType;
};

function normalizeStatus(status: string | null | undefined): string {
  const normalized = status?.trim().toUpperCase();
  if (!normalized) {
    return 'NO_DATA';
  }
  if (normalized === 'FAILED') {
    return 'FAIL';
  }
  return normalized;
}

function statusTone(status: string | null | undefined): StatusTone {
  const normalized = normalizeStatus(status);

  if (normalized === 'SUCCESS' || normalized === 'NORMAL' || normalized === 'ACTIVE') {
    return {
      color: '#1b7f47',
      backgroundColor: '#e8f7ee',
      borderColor: '#b9e5c9',
    };
  }
  if (normalized === 'WARNING' || normalized === 'DATA_EMPTY') {
    return {
      color: '#b85a00',
      backgroundColor: '#fff3e7',
      borderColor: '#ffd4ad',
    };
  }
  if (normalized === 'FAIL' || normalized === 'CRITICAL') {
    return {
      color: '#c1272d',
      backgroundColor: '#ffebee',
      borderColor: '#ffc9cf',
    };
  }
  if (normalized === 'RUNNING') {
    return {
      color: '#145dc9',
      backgroundColor: '#e8f0ff',
      borderColor: '#c4d8ff',
    };
  }
  return {
    color: '#606a78',
    backgroundColor: '#f3f5f8',
    borderColor: '#d8dce3',
  };
}

function formatDateTimeKst(value: string | null | undefined): string {
  if (!value) {
    return '데이터 없음';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return '데이터 없음';
  }
  return parsed.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Seoul',
  });
}

function formatCount(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) {
    return '0';
  }
  return Math.max(0, Number(value)).toLocaleString('ko-KR');
}

function formatPercent(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) {
    return '-';
  }
  return `${(value * 100).toFixed(2)}%`;
}

function runStatusLabel(status: string | null | undefined): string {
  const normalized = normalizeStatus(status);
  if (normalized === 'SUCCESS') return '성공';
  if (normalized === 'RUNNING') return '실행 중';
  if (normalized === 'FAIL') return '실패';
  if (normalized === 'SKIPPED') return '건너뜀';
  return '데이터 없음';
}

function supervisedStatusLabel(status: string | null | undefined): string {
  const normalized = normalizeStatus(status);
  if (normalized === 'SUCCESS') return '성공';
  if (normalized === 'WARNING' || normalized === 'DATA_EMPTY') return '주의';
  if (normalized === 'FAIL') return '실패';
  if (normalized === 'RUNNING') return '실행 중';
  if (normalized === 'SKIPPED') return '건너뜀';
  if (normalized === 'NORMAL') return '정상';
  return '데이터 없음';
}

function resultStatusLabel(status: string | null | undefined): string {
  const normalized = normalizeStatus(status);
  if (normalized === 'WARNING') return '주의 필요';
  if (normalized === 'NORMAL') return '정상';
  return '데이터 없음';
}

function analysisTypeLabel(analysisType: string): string {
  if (analysisType === 'SUPERVISED_CLASSIFICATION') {
    return '지도학습 분석';
  }
  if (analysisType === 'ANOMALY_DETECTION') {
    return '이상탐지';
  }
  return '운영 상태';
}

function modelTypeLabel(modelType: string | null | undefined): string {
  const normalized = modelType?.trim().toUpperCase();
  if (normalized === 'SUPERVISED_CLASSIFICATION') {
    return '분류';
  }
  return '이상탐지';
}

function formatAlgorithmLabel(value: string | null | undefined): string {
  const trimmed = value?.trim();
  if (!trimmed) {
    return '-';
  }
  if (!trimmed.includes('_')) {
    return trimmed;
  }
  return trimmed
    .toLowerCase()
    .split('_')
    .map((token) => (token ? token.charAt(0).toUpperCase() + token.slice(1) : token))
    .join(' ');
}

function resolveAnomalyRowSummary(run: HomeDashboardRecentRun): string {
  if (run.analysisType === 'SUPERVISED_CLASSIFICATION') {
    return `분류 결과 ${formatCount(run.resultCount)}건`;
  }
  if (run.analysisType === 'ANOMALY_DETECTION') {
    return `이상 ${formatCount(run.anomalyCount ?? 0)}건 / 전체 ${formatCount(run.resultCount)}건`;
  }
  return `결과 ${formatCount(run.resultCount)}건`;
}

function buildMiniCorrelationMatrix(size = 5): number[][] {
  const matrix: number[][] = [];
  for (let row = 0; row < size; row += 1) {
    const values: number[] = [];
    for (let col = 0; col < size; col += 1) {
      if (row === col) {
        values.push(1);
        continue;
      }
      const seed = Math.sin((row + 1) * (col + 2) * 1.77);
      values.push(seed);
    }
    matrix.push(values);
  }
  return matrix;
}

function correlationCellColor(value: number): string {
  const intensity = Math.min(1, Math.abs(value));
  const alpha = 0.16 + intensity * 0.54;

  if (value >= 0.6) return `rgba(76, 145, 255, ${alpha})`;
  if (value >= 0.2) return `rgba(132, 193, 255, ${alpha})`;
  if (value <= -0.6) return `rgba(245, 111, 111, ${alpha})`;
  if (value <= -0.2) return `rgba(255, 171, 102, ${alpha})`;
  return `rgba(210, 219, 235, ${alpha})`;
}

function normalizeDashboard(data: HomeDashboardResponse | null | undefined): HomeDashboardResponse {
  if (!data) {
    return EMPTY_DASHBOARD;
  }

  return {
    syncedAt: data.syncedAt ?? null,
    kpi: {
      ...EMPTY_DASHBOARD.kpi,
      ...(data.kpi ?? {}),
    },
    activeModels: data.activeModels ?? [],
    anomalyTrend: data.anomalyTrend ?? [],
    correlationSummary: {
      ...EMPTY_DASHBOARD.correlationSummary,
      ...(data.correlationSummary ?? {}),
    },
    recentRuns: data.recentRuns ?? [],
    latestSupervised: {
      ...EMPTY_DASHBOARD.latestSupervised,
      ...(data.latestSupervised ?? {}),
    },
  };
}

export function HomePage() {
  const navigate = useNavigate();
  const { menus } = useOutletContext<MainLayoutOutletContext>();

  const [dashboard, setDashboard] = useState<HomeDashboardResponse>(EMPTY_DASHBOARD);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [localSyncedAt, setLocalSyncedAt] = useState<string | null>(null);

  const loadDashboard = useCallback(async (isRefresh: boolean) => {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }

    try {
      const response = await homeService.fetchDashboard();
      setDashboard(normalizeDashboard(response));
      setErrorMessage(null);
    } catch (error) {
      setDashboard(EMPTY_DASHBOARD);
      setErrorMessage(error instanceof Error ? error.message : '홈 대시보드 정보를 불러오지 못했습니다.');
    } finally {
      setLocalSyncedAt(new Date().toISOString());
      if (isRefresh) {
        setRefreshing(false);
      } else {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadDashboard(false);
  }, [loadDashboard]);

  const accessiblePaths = useMemo(() => extractAccessiblePaths(menus), [menus]);

  const routePaths = useMemo(
    () => ({
      anomaly: getRoutePathById('ai-anomaly'),
      supervised: getRoutePathById('ai-supervised-result'),
      overview: getRoutePathById('ai-overview'),
      correlation: getRoutePathById('data-exploration-correlation-heatmap'),
      modeltrain: getRoutePathById('operation-modeltrain'),
      algorithm: getRoutePathById('operation-algorithm'),
      dataHistogram: getRoutePathById('data-exploration-histogram'),
      user: getRoutePathById('operation-user'),
    }),
    [],
  );

  const anomalyPath = accessiblePaths.has(routePaths.anomaly) ? routePaths.anomaly : null;
  const supervisedPath = accessiblePaths.has(routePaths.supervised) ? routePaths.supervised : null;
  const overviewPath = accessiblePaths.has(routePaths.overview) ? routePaths.overview : null;
  const correlationPath = accessiblePaths.has(routePaths.correlation) ? routePaths.correlation : null;
  const modelPolicyPath = accessiblePaths.has(routePaths.overview)
    ? routePaths.overview
    : accessiblePaths.has(routePaths.modeltrain)
      ? routePaths.modeltrain
      : null;
  const supervisedSummaryPath = supervisedPath ?? modelPolicyPath;
  const supervisedSummaryActionLabel = supervisedPath ? '지도 학습 결과로 이동' : '모델/정책 관리로 이동';

  const kpiCards = useMemo<KpiCardConfig[]>(() => {
    const { kpi } = dashboard;
    return [
      {
        id: 'active-model',
        title: '활성 모델',
        description: '지도학습/이상탐지 모델 포함',
        icon: SmartToyRoundedIcon,
        value: `${formatCount(kpi.activeModelCount)}개`,
        subValue: 'tmst_model_active use_flag=Y',
        statusCode: 'ACTIVE',
      },
      {
        id: 'latest-run',
        title: '최근 분석 상태',
        description: '최근 실행 시각',
        icon: TaskAltRoundedIcon,
        value: runStatusLabel(kpi.latestRunStatus),
        subValue: formatDateTimeKst(kpi.latestRunAt),
        statusCode: kpi.latestRunStatus,
      },
      {
        id: 'latest-result',
        title: '최근 결과 상태',
        description: '비지도/지도학습 최신 결과 기준',
        icon: PolicyRoundedIcon,
        value: resultStatusLabel(kpi.latestResultStatus),
        subValue: `이상 ${formatCount(kpi.anomalyCount)}건 / 전체 ${formatCount(kpi.totalResultCount)}건`,
        statusCode: kpi.latestResultStatus,
      },
      {
        id: 'dataset',
        title: '분석 데이터',
        description: 'dataset key / 메타 필드 수',
        icon: StorageRoundedIcon,
        value: `${formatCount(kpi.fieldCount)}개 필드`,
        subValue: `dataset ${formatCount(kpi.datasetCount)}개 / 메타 ${formatCount(kpi.fieldCount)}개`,
      },
      {
        id: 'latest-update',
        title: '최근 업데이트',
        description: 'KST 기준 표시',
        icon: UpdateRoundedIcon,
        value: formatDateTimeKst(kpi.latestUpdatedAt),
        subValue: 'thismodelrun, 결과 컬렉션 최신 기준',
      },
    ];
  }, [dashboard]);

  const trendPoints = dashboard.anomalyTrend;
  const totalTrendCount = trendPoints.reduce((sum, point) => sum + Math.max(point.count ?? 0, 0), 0);
  const maxTrendCount = Math.max(1, ...trendPoints.map((point) => Math.max(point.count ?? 0, 0)));
  const hasTrendData = trendPoints.length > 0;
  const hasNonZeroTrend = trendPoints.some((point) => (point.count ?? 0) > 0);
  const latestSupervisedRun = useMemo(
    () =>
      dashboard.recentRuns.find(
        (run) =>
          run.analysisType === 'SUPERVISED_CLASSIFICATION' &&
          run.runId === dashboard.latestSupervised.runId,
      ) ??
      dashboard.recentRuns.find((run) => run.analysisType === 'SUPERVISED_CLASSIFICATION') ??
      null,
    [dashboard.latestSupervised.runId, dashboard.recentRuns],
  );
  const supervisedActiveModel = useMemo(
    () =>
      dashboard.activeModels.find(
        (model) => model.modelType?.trim().toUpperCase() === 'SUPERVISED_CLASSIFICATION',
      ) ?? null,
    [dashboard.activeModels],
  );
  const supervisedAlgorithmName = useMemo(() => {
    if (latestSupervisedRun?.algoName?.trim()) {
      return latestSupervisedRun.algoName.trim();
    }
    if (latestSupervisedRun?.algoCode?.trim()) {
      return formatAlgorithmLabel(latestSupervisedRun.algoCode);
    }
    if (supervisedActiveModel?.algoName?.trim()) {
      return supervisedActiveModel.algoName.trim();
    }
    if (supervisedActiveModel?.algoCode?.trim()) {
      return formatAlgorithmLabel(supervisedActiveModel.algoCode);
    }
    return '-';
  }, [latestSupervisedRun, supervisedActiveModel]);
  const latestSupervisedTone = statusTone(dashboard.latestSupervised.status);
  const latestSupervisedStatus = supervisedStatusLabel(dashboard.latestSupervised.status);
  const latestSupervisedMessage = dashboard.latestSupervised.message?.trim() || '최근 지도 학습 결과가 없습니다.';

  const quickMenuConfigs: QuickMenuConfig[] = [
    {
      id: 'data-status',
      title: '데이터 현황',
      description: '데이터셋, 컬럼, 원본 데이터 확인',
      routeId: 'data-exploration-histogram',
      icon: DataObjectRoundedIcon,
    },
    {
      id: 'anomaly',
      title: '이상탐지 실행/결과',
      description: '이상탐지 모델 실행 및 결과 확인',
      routeId: 'ai-anomaly',
      icon: WarningAmberRoundedIcon,
    },
    {
      id: 'supervised',
      title: '지도학습 분석',
      description: '분류 모델 학습 및 예측 결과 확인',
      routeId: 'ai-supervised-result',
      icon: ModelTrainingRoundedIcon,
    },
    {
      id: 'overview',
      title: 'AI 운영 상태',
      description: '활성 모델과 최근 실행 상태 확인',
      routeId: 'ai-overview',
      icon: MonitorHeartRoundedIcon,
    },
    {
      id: 'model-manage',
      title: '모델 관리',
      description: '알고리즘 선택 및 정책 관리',
      routeId: 'operation-algorithm',
      icon: AccountTreeRoundedIcon,
    },
    {
      id: 'settings',
      title: '환경 설정',
      description: '기준 정보 및 운영 설정 관리',
      routeId: 'operation-user',
      icon: SettingsSuggestRoundedIcon,
    },
  ];

  const miniMatrix = useMemo(() => buildMiniCorrelationMatrix(5), []);

  const syncedAtText = formatDateTimeKst(dashboard.syncedAt ?? localSyncedAt);

  const resolveRecentRunActionPath = useCallback(
    (run: HomeDashboardRecentRun): string | null => {
      if (run.analysisType === 'SUPERVISED_CLASSIFICATION') {
        return supervisedPath ?? overviewPath;
      }
      if (run.analysisType === 'ANOMALY_DETECTION') {
        return anomalyPath ?? overviewPath;
      }
      return overviewPath;
    },
    [anomalyPath, overviewPath, supervisedPath],
  );

  const renderSummaryCards = () => {
    if (loading) {
      return (
        <Box
          sx={{
            display: 'grid',
            gap: 1,
            gridTemplateColumns: {
              xs: '1fr',
              sm: 'repeat(2, minmax(0, 1fr))',
              lg: 'repeat(5, minmax(0, 1fr))',
            },
          }}
        >
          {Array.from({ length: 5 }).map((_, index) => (
            <Card key={index} variant="outlined" sx={{ borderColor: '#d7e3f2' }}>
              <CardContent sx={{ py: 1.15 }}>
                <Skeleton width={92} height={18} />
                <Skeleton width="68%" height={32} sx={{ mt: 0.55 }} />
                <Skeleton width="100%" height={16} sx={{ mt: 0.35 }} />
              </CardContent>
            </Card>
          ))}
        </Box>
      );
    }

    return (
      <Box
        sx={{
          display: 'grid',
          gap: 1,
          gridTemplateColumns: {
            xs: '1fr',
            sm: 'repeat(2, minmax(0, 1fr))',
            lg: 'repeat(5, minmax(0, 1fr))',
          },
        }}
      >
        {kpiCards.map((card) => {
          const tone = statusTone(card.statusCode);
          const CardIcon = card.icon;

          return (
            <Card key={card.id} variant="outlined" sx={{ borderColor: '#d7e3f2' }}>
              <CardContent sx={{ px: 1.4, py: 1.15 }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, fontSize: 12.5 }}>
                    {card.title}
                  </Typography>
                  <Box
                    sx={{
                      width: 30,
                      height: 30,
                      borderRadius: '50%',
                      display: 'grid',
                      placeItems: 'center',
                      color: tone.color,
                      bgcolor: tone.backgroundColor,
                    }}
                  >
                    <CardIcon sx={{ fontSize: 17 }} />
                  </Box>
                </Stack>

                <Typography
                  variant="h6"
                  sx={{
                    mt: 0.7,
                    mb: 0.5,
                    fontWeight: 800,
                    color: tone.color,
                    lineHeight: 1.22,
                    minHeight: 34,
                    fontSize: 26,
                  }}
                >
                  {card.value}
                </Typography>

                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.45, display: 'block' }}>
                  {card.description}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.45, display: 'block' }}>
                  {card.subValue}
                </Typography>
              </CardContent>
            </Card>
          );
        })}
      </Box>
    );
  };

  const renderMiddleSection = () => {
    return (
      <Box
        sx={{
          display: 'grid',
          gap: 1,
          gridTemplateColumns: {
            xs: '1fr',
            lg: '1.2fr 1fr 1fr',
          },
        }}
      >
        <Card variant="outlined" sx={{ borderColor: '#d7e3f2' }}>
          <CardContent sx={{ px: 1.45, py: 1.2 }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                  이상 탐지 / 지도 학습 현황
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  최근 7일간 이상 탐지 발생 건수 추이
                </Typography>
              </Box>
              <Chip
                size="small"
                label={`합계 ${formatCount(totalTrendCount)}건`}
                sx={{ fontWeight: 700, bgcolor: '#edf3ff' }}
              />
            </Stack>

            <Divider sx={{ my: 0.9 }} />

            {!hasTrendData ? (
              <Typography variant="body2" color="text.secondary">
                최근 이상 탐지 추이 데이터가 없습니다.
              </Typography>
            ) : (
              <Stack spacing={0.65}>
                {!hasNonZeroTrend && (
                  <Typography variant="caption" color="text.secondary">
                    최근 7일 이상 건수가 모두 0건입니다.
                  </Typography>
                )}

                {trendPoints.map((point: HomeDashboardAnomalyTrendPoint, index) => {
                  const count = Math.max(point.count ?? 0, 0);
                  const ratio = (count / maxTrendCount) * 100;
                  const tone = count > 0 ? '#2f6de8' : '#d2dcee';

                  return (
                    <Box
                      key={`${point.date}-${index}`}
                      sx={{
                        display: 'grid',
                        gridTemplateColumns: '86px minmax(0, 1fr) 50px',
                        gap: 0.7,
                        alignItems: 'center',
                      }}
                    >
                      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                        {point.date}
                      </Typography>
                      <Box sx={{ height: 10, borderRadius: 99, bgcolor: '#edf2fb', overflow: 'hidden' }}>
                        <Box
                          sx={{
                            width: `${Math.max(0, Math.min(100, ratio))}%`,
                            minWidth: count > 0 ? 6 : 0,
                            height: '100%',
                            borderRadius: 99,
                            bgcolor: tone,
                          }}
                        />
                      </Box>
                      <Typography variant="caption" sx={{ fontWeight: 700, textAlign: 'right' }}>
                        {formatCount(count)}건
                      </Typography>
                    </Box>
                  );
                })}
              </Stack>
            )}

            {anomalyPath && (
              <Button
                size="small"
                variant="text"
                sx={{ mt: 1.1, px: 0, fontWeight: 700 }}
                onClick={() => navigate(anomalyPath)}
              >
                이상 탐지 결과로 이동
              </Button>
            )}

            <Divider sx={{ my: 1.1 }} />

            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
              지도 학습 현황
            </Typography>
            {dashboard.latestSupervised.available ? (
              <Box
                sx={{
                  mt: 0.75,
                  border: '1px solid #d7e3f2',
                  borderRadius: 1.4,
                  bgcolor: '#f9fbff',
                  overflow: 'hidden',
                }}
              >
                <Box sx={{ px: 1.1, py: 0.95 }}>
                  <Box
                    sx={{
                      display: 'grid',
                      gap: 0.85,
                      gridTemplateColumns: {
                        xs: '1fr',
                        sm: 'repeat(2, minmax(0, 1fr))',
                        lg: '1.3fr 1.5fr 1fr 1fr',
                      },
                    }}
                  >
                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        알고리즘
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 800 }}>
                        {supervisedAlgorithmName}
                      </Typography>
                    </Box>

                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        최근 실행 시각
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        {formatDateTimeKst(dashboard.latestSupervised.runAt)}
                      </Typography>
                    </Box>

                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        결과 상태
                      </Typography>
                      <Box sx={{ mt: 0.2 }}>
                        <Chip
                          size="small"
                          label={latestSupervisedStatus}
                          sx={{
                            height: 23,
                            fontWeight: 700,
                            color: latestSupervisedTone.color,
                            bgcolor: latestSupervisedTone.backgroundColor,
                            border: `1px solid ${latestSupervisedTone.borderColor}`,
                          }}
                        />
                      </Box>
                    </Box>

                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        결과 건수
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 800 }}>
                        {formatCount(dashboard.latestSupervised.resultCount)}건
                      </Typography>
                    </Box>
                  </Box>
                </Box>

                <Divider />

                <Box sx={{ px: 1.1, py: 0.95 }}>
                  <Box
                    sx={{
                      display: 'grid',
                      gap: 0.8,
                      gridTemplateColumns: {
                        xs: '1fr',
                        sm: 'repeat(3, minmax(0, 1fr))',
                      },
                    }}
                  >
                    <Box sx={{ borderRight: { xs: 'none', sm: '1px solid #e6eef9' }, pr: { xs: 0, sm: 0.8 } }}>
                      <Typography variant="caption" color="text.secondary">
                        Accuracy
                      </Typography>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800, color: '#1660cf' }}>
                        {formatPercent(dashboard.latestSupervised.accuracy)}
                      </Typography>
                    </Box>
                    <Box
                      sx={{
                        borderLeft: { xs: 'none', sm: '1px solid #eef3fb' },
                        borderRight: { xs: 'none', sm: '1px solid #e6eef9' },
                        px: { xs: 0, sm: 0.8 },
                      }}
                    >
                      <Typography variant="caption" color="text.secondary">
                        Precision
                      </Typography>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800, color: '#1660cf' }}>
                        {formatPercent(dashboard.latestSupervised.precision)}
                      </Typography>
                    </Box>
                    <Box sx={{ borderLeft: { xs: 'none', sm: '1px solid #eef3fb' }, pl: { xs: 0, sm: 0.8 } }}>
                      <Typography variant="caption" color="text.secondary">
                        F1 Score
                      </Typography>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800, color: '#1660cf' }}>
                        {formatPercent(dashboard.latestSupervised.f1Score)}
                      </Typography>
                    </Box>
                  </Box>
                </Box>
              </Box>
            ) : (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                {latestSupervisedMessage}
              </Typography>
            )}

            {supervisedSummaryPath && (
              <Button
                size="small"
                variant="text"
                sx={{ mt: 0.9, px: 0, fontWeight: 700 }}
                onClick={() => navigate(supervisedSummaryPath)}
              >
                {supervisedSummaryActionLabel}
              </Button>
            )}
          </CardContent>
        </Card>

        <Card variant="outlined" sx={{ borderColor: '#d7e3f2' }}>
          <CardContent sx={{ px: 1.45, py: 1.2 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              상관관계 요약
            </Typography>
            <Typography variant="caption" color="text.secondary">
              선택 컬럼 기준 상관관계 히트맵 요약
            </Typography>

            <Divider sx={{ my: 0.9 }} />

            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: '20px repeat(5, minmax(0, 1fr))',
                gridTemplateRows: '16px repeat(5, minmax(0, 1fr))',
                gap: 0.2,
                p: 0.45,
                borderRadius: 1,
                border: '1px solid #e2ebf8',
                bgcolor: '#f8fbff',
              }}
            >
              <Box />
              {['A', 'B', 'C', 'D', 'E'].map((axis) => (
                <Typography
                  key={`x-${axis}`}
                  variant="caption"
                  sx={{ fontSize: 9, textAlign: 'center', color: 'text.secondary' }}
                >
                  {axis}
                </Typography>
              ))}

              {miniMatrix.map((row, rowIndex) => (
                <Box key={`row-${rowIndex}`} sx={{ display: 'contents' }}>
                  <Typography
                    variant="caption"
                    sx={{ fontSize: 9, textAlign: 'center', color: 'text.secondary', lineHeight: '24px' }}
                  >
                    {['A', 'B', 'C', 'D', 'E'][rowIndex]}
                  </Typography>
                  {row.map((value, colIndex) => (
                    <Box
                      key={`${rowIndex}-${colIndex}`}
                      sx={{
                        height: 44,
                        borderRadius: 0.4,
                        bgcolor: correlationCellColor(value),
                        border:
                          rowIndex === colIndex
                            ? '1px solid rgba(76,145,255,0.36)'
                            : '1px solid rgba(255,255,255,0.28)',
                      }}
                    />
                  ))}
                </Box>
              ))}
            </Box>

            <Typography variant="body2" sx={{ mt: 0.8, fontWeight: 700 }}>
              선택 컬럼 {formatCount(dashboard.correlationSummary.fieldCount)}개 기준 요약
            </Typography>
            {!dashboard.correlationSummary.available && (
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.35, display: 'block' }}>
                {dashboard.correlationSummary.message}
              </Typography>
            )}

            <Stack direction="row" spacing={0.7} sx={{ mt: 1 }}>
              <Chip
                size="small"
                label={`상관쌍 ${formatCount(
                  dashboard.correlationSummary.fieldCount >= 2
                    ? (dashboard.correlationSummary.fieldCount *
                        (dashboard.correlationSummary.fieldCount - 1)) /
                        2
                    : 0,
                )}개`}
                sx={{ fontWeight: 700, bgcolor: '#f1f6ff' }}
              />
              {correlationPath && (
                <Button size="small" variant="text" sx={{ px: 0, fontWeight: 700 }} onClick={() => navigate(correlationPath)}>
                  상세 이동
                </Button>
              )}
            </Stack>
          </CardContent>
        </Card>

        <Card variant="outlined" sx={{ borderColor: '#d7e3f2' }}>
          <CardContent sx={{ px: 1.45, py: 1.2 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              활성 모델 / 정책
            </Typography>
            <Typography variant="caption" color="text.secondary">
              현재 운영 중인 모델과 정책 정보
            </Typography>
            <Divider sx={{ my: 0.9 }} />

            {dashboard.activeModels.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                활성 모델이 없습니다.
              </Typography>
            ) : (
              <Stack spacing={0.75}>
                {dashboard.activeModels.map((model: HomeDashboardActiveModel, index) => {
                  const tone = statusTone(model.status);
                  return (
                    <Box
                      key={`${model.datasetKey ?? 'dataset'}-${model.algoCode ?? 'algo'}-${index}`}
                      sx={{
                        border: '1px solid #e0e8f5',
                        borderRadius: 1.3,
                        px: 1,
                        py: 0.85,
                        bgcolor: '#fbfdff',
                      }}
                    >
                      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={0.6}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                          {model.algoName ?? model.algoCode ?? '모델 미정'}
                        </Typography>
                        <Stack direction="row" spacing={0.4}>
                          <Chip
                            size="small"
                            label={modelTypeLabel(model.modelType)}
                            sx={{ height: 22, fontWeight: 700, bgcolor: '#eef3ff' }}
                          />
                          <Chip
                            size="small"
                            label={model.status ?? 'NO_DATA'}
                            sx={{
                              height: 22,
                              fontWeight: 700,
                              color: tone.color,
                              bgcolor: tone.backgroundColor,
                              border: `1px solid ${tone.borderColor}`,
                            }}
                          />
                        </Stack>
                      </Stack>
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 0.35, display: 'block' }}>
                        policy {model.policyId ?? '-'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        dataset {model.datasetKey ?? '-'}
                      </Typography>
                    </Box>
                  );
                })}
              </Stack>
            )}

            <Divider sx={{ my: 0.9 }} />

            <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.5 }}>
              지도학습 최근 요약
            </Typography>
            {dashboard.latestSupervised.available ? (
              <Stack spacing={0.35}>
                <Typography variant="caption" color="text.secondary">
                  실행 {formatDateTimeKst(dashboard.latestSupervised.runAt)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  상태 {runStatusLabel(dashboard.latestSupervised.status)} / 결과 {formatCount(dashboard.latestSupervised.resultCount)}건
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Accuracy {formatPercent(dashboard.latestSupervised.accuracy)} · Precision {formatPercent(dashboard.latestSupervised.precision)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Recall {formatPercent(dashboard.latestSupervised.recall)} · F1 {formatPercent(dashboard.latestSupervised.f1Score)}
                </Typography>
              </Stack>
            ) : (
              <Typography variant="caption" color="text.secondary">
                지도학습 결과 데이터가 없습니다.
              </Typography>
            )}

            {modelPolicyPath && (
              <Button size="small" variant="text" sx={{ mt: 1, px: 0, fontWeight: 700 }} onClick={() => navigate(modelPolicyPath)}>
                모델/정책 관리로 이동
              </Button>
            )}
          </CardContent>
        </Card>
      </Box>
    );
  };

  const renderBottomSection = () => {
    return (
      <Box
        sx={{
          display: 'grid',
          gap: 1,
          gridTemplateColumns: {
            xs: '1fr',
            lg: '1.7fr 1fr',
          },
        }}
      >
        <Card variant="outlined" sx={{ borderColor: '#d7e3f2' }}>
          <CardContent sx={{ px: 1.45, py: 1.2 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              최근 분석 결과
            </Typography>
            <Typography variant="caption" color="text.secondary">
              최근 실행된 분석 결과를 확인하세요.
            </Typography>
            <Divider sx={{ my: 0.9 }} />

            {dashboard.recentRuns.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                최근 실행된 분석 결과가 없습니다.
              </Typography>
            ) : (
              <TableContainer
                sx={{
                  border: '1px solid #e2ebf8',
                  borderRadius: 1.2,
                  overflowX: 'auto',
                }}
              >
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: '#f6f9ff' }}>
                      <TableCell sx={{ fontWeight: 800 }}>실행 시간</TableCell>
                      <TableCell sx={{ fontWeight: 800 }}>분석 유형</TableCell>
                      <TableCell sx={{ fontWeight: 800 }}>데이터셋</TableCell>
                      <TableCell sx={{ fontWeight: 800 }}>결과 상태</TableCell>
                      <TableCell sx={{ fontWeight: 800 }}>결과 요약</TableCell>
                      <TableCell sx={{ fontWeight: 800, minWidth: 94 }}>작업</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {dashboard.recentRuns.map((run) => {
                      const tone = statusTone(run.status);
                      const actionPath = resolveRecentRunActionPath(run);
                      return (
                        <TableRow key={run.runId} hover>
                          <TableCell>{formatDateTimeKst(run.runAt)}</TableCell>
                          <TableCell>{analysisTypeLabel(run.analysisType)}</TableCell>
                          <TableCell>{run.datasetKey ?? '-'}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={runStatusLabel(run.status)}
                              sx={{
                                height: 23,
                                fontWeight: 700,
                                color: tone.color,
                                bgcolor: tone.backgroundColor,
                                border: `1px solid ${tone.borderColor}`,
                              }}
                            />
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" sx={{ fontWeight: 700 }}>
                              {resolveAnomalyRowSummary(run)}
                            </Typography>
                            {run.analysisType === 'SUPERVISED_CLASSIFICATION' && (
                              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                                Acc {formatPercent(run.accuracy)} / F1 {formatPercent(run.f1Score)}
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell>
                            <Button
                              size="small"
                              variant={actionPath ? 'outlined' : 'text'}
                              disabled={!actionPath}
                              onClick={() => {
                                if (actionPath) {
                                  navigate(actionPath);
                                }
                              }}
                            >
                              결과 보기
                            </Button>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </CardContent>
        </Card>

        <Card variant="outlined" sx={{ borderColor: '#d7e3f2' }}>
          <CardContent sx={{ px: 1.45, py: 1.2 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              빠른 메뉴
            </Typography>
            <Typography variant="caption" color="text.secondary">
              자주 사용하는 메뉴로 바로 이동하세요.
            </Typography>
            <Divider sx={{ my: 0.9 }} />

            <Box
              sx={{
                display: 'grid',
                gap: 0.8,
                gridTemplateColumns: {
                  xs: '1fr',
                  sm: 'repeat(2, minmax(0, 1fr))',
                  lg: 'repeat(2, minmax(0, 1fr))',
                },
              }}
            >
              {quickMenuConfigs.map((menu) => {
                const path = getRoutePathById(menu.routeId);
                const enabled = accessiblePaths.has(path);
                const IconComponent = menu.icon;

                return (
                  <Card
                    key={menu.id}
                    variant="outlined"
                    sx={{
                      borderColor: enabled ? '#d7e3f2' : '#e5e9f0',
                      bgcolor: enabled ? '#fbfdff' : '#f7f8fa',
                      opacity: enabled ? 1 : 0.6,
                    }}
                  >
                    <CardContent sx={{ px: 1.05, py: 0.95 }}>
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <Box
                          sx={{
                            width: 30,
                            height: 30,
                            borderRadius: '50%',
                            display: 'grid',
                            placeItems: 'center',
                            bgcolor: enabled ? '#ecf3ff' : '#eceff3',
                            color: enabled ? '#1660cf' : '#98a1af',
                          }}
                        >
                          <IconComponent sx={{ fontSize: 18 }} />
                        </Box>
                        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                          {menu.title}
                        </Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 0.6, display: 'block', minHeight: 32 }}>
                        {menu.description}
                      </Typography>
                      <Button
                        size="small"
                        variant={enabled ? 'contained' : 'outlined'}
                        disabled={!enabled}
                        sx={{ mt: 0.55 }}
                        onClick={() => {
                          if (enabled) {
                            navigate(path);
                          }
                        }}
                      >
                        {enabled ? '바로 이동' : '권한 없음'}
                      </Button>
                    </CardContent>
                  </Card>
                );
              })}
            </Box>
          </CardContent>
        </Card>
      </Box>
    );
  };

  return (
    <Stack spacing={1} sx={{ pb: 1 }}>
      <Card
        variant="outlined"
        sx={{
          borderColor: '#d7e3f2',
          background: 'linear-gradient(180deg, #fbfdff 0%, #f7fbff 100%)',
        }}
      >
        <CardContent sx={{ px: 1.45, py: 1.15 }}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            spacing={0.9}
          >
            <Box>
              <Typography variant="h5" sx={{ fontWeight: 800, lineHeight: 1.2, fontSize: { xs: 21, md: 23 } }}>
                홈 대시보드
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
                주요 설비 AI 분석 상태와 최근 결과를 한눈에 확인합니다.
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.35, display: 'block' }}>
                마지막 동기화: {syncedAtText}
              </Typography>
            </Box>

            <Button
              variant="outlined"
              size="small"
              startIcon={<RefreshRoundedIcon />}
              disabled={refreshing}
              onClick={() => void loadDashboard(true)}
            >
              {refreshing ? '새로고침 중...' : '새로고침'}
            </Button>
          </Stack>
        </CardContent>
      </Card>

      {errorMessage && <Alert severity="warning">{errorMessage}</Alert>}

      {renderSummaryCards()}
      {renderMiddleSection()}
      {renderBottomSection()}
    </Stack>
  );
}
