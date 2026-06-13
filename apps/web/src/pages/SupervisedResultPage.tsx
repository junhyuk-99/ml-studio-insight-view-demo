import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  LinearProgress,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { supervisedResultService } from '../services/supervisedResultService';
import type {
  SupervisedDistribution,
  SupervisedDistributionItem,
  SupervisedErrorRow,
  SupervisedErrors,
  SupervisedFeatureImportance,
  SupervisedMetric,
  SupervisedPredictionPage,
  SupervisedPredictionRow,
  SupervisedRun,
  SupervisedSummary,
  SupervisedTrendPoint,
} from '../types/supervisedResult';

const LOAD_ERROR_MESSAGE = '지도학습 분석 결과를 불러오지 못했습니다.';
const NO_RUNS_MESSAGE = '조회 가능한 Random Forest 성공 실행 이력이 없습니다.';
const NO_PREDICTION_MESSAGE = '조건에 해당하는 예측 결과가 없습니다.';
const NO_FEATURE_IMPORTANCE_MESSAGE = 'Feature Importance 데이터가 없습니다. 다음 Random Forest 실행 후 표시됩니다.';

const TRIGGER_OPTIONS = [
  { value: 'ALL', label: '전체' },
  { value: 'MANUAL', label: 'MANUAL' },
  { value: 'SCHEDULE', label: 'SCHEDULE' },
] as const;

const PREDICTION_FILTER_OPTIONS = [
  { value: 'ALL', label: '전체' },
  { value: 'CORRECT', label: '정답' },
  { value: 'INCORRECT', label: '오답' },
  { value: 'TP', label: 'TP' },
  { value: 'TN', label: 'TN' },
  { value: 'FP', label: 'FP' },
  { value: 'FN', label: 'FN' },
] as const;

const EMPTY_METRIC: SupervisedMetric = {
  key: '',
  label: '',
  value: null,
  numerator: 0,
  denominator: 0,
};

const EMPTY_DISTRIBUTION: SupervisedDistribution = {
  runId: '',
  totalCount: 0,
  tp: 0,
  tn: 0,
  fp: 0,
  fn: 0,
  items: [],
};

const EMPTY_ERRORS: SupervisedErrors = {
  runId: '',
  fpTop: [],
  fnTop: [],
};

const EMPTY_PREDICTION_PAGE: SupervisedPredictionPage = {
  items: [],
  total: 0,
  page: 0,
  size: 50,
  totalPages: 0,
};

function normalizeText(value: string | null | undefined): string | null {
  const normalized = value?.trim();
  if (!normalized) {
    return null;
  }
  return normalized;
}

function formatDateTime(value: string | null | undefined): string {
  const normalized = normalizeText(value);
  if (!normalized) {
    return '-';
  }
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) {
    return normalized;
  }
  return date.toLocaleString('ko-KR', { hour12: false, timeZone: 'Asia/Seoul' });
}

function formatPercent(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '-';
  }
  return `${(value * 100).toFixed(2)}%`;
}

function formatNumber(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '-';
  }
  return value.toLocaleString('ko-KR');
}

function formatFraction(numerator: number, denominator: number): string {
  return `${numerator.toLocaleString('ko-KR')} / ${denominator.toLocaleString('ko-KR')}`;
}

function formatProbability(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '-';
  }
  return value.toFixed(2);
}

function formatImportance(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '0.000';
  }
  return value.toFixed(3);
}

function toPercent(value: number | null | undefined): number {
  if (value == null || Number.isNaN(value)) {
    return 0;
  }
  return Math.max(0, Math.min(value * 100, 100));
}

function formatAxisDateTime(value: string | null | undefined): string {
  const normalized = normalizeText(value);
  if (!normalized) {
    return '-';
  }
  const parsed = new Date(normalized);
  if (Number.isNaN(parsed.getTime())) {
    return normalized;
  }
  return parsed.toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Seoul',
  });
}

function resolveErrorMessage(error: unknown, fallbackMessage: string): string {
  return error instanceof Error ? error.message : fallbackMessage;
}

function toUtcIso(datetimeLocal: string): string | null {
  const normalized = datetimeLocal.trim();
  if (!normalized) {
    return null;
  }
  const parsed = new Date(normalized);
  if (Number.isNaN(parsed.getTime())) {
    return normalized;
  }
  return parsed.toISOString();
}

function metricTone(metricValue: number | null | undefined): { valueColor: string; trackColor: string; barColor: string } {
  if (metricValue == null || Number.isNaN(metricValue)) {
    return { valueColor: '#546e7a', trackColor: '#eceff1', barColor: '#90a4ae' };
  }
  const percent = metricValue * 100;
  if (percent >= 90) {
    return { valueColor: '#137333', trackColor: '#e8f5e9', barColor: '#2e7d32' };
  }
  if (percent >= 70) {
    return { valueColor: '#a84300', trackColor: '#fff3e0', barColor: '#ef6c00' };
  }
  return { valueColor: '#b71c1c', trackColor: '#ffebee', barColor: '#d32f2f' };
}

function badge(label: string, color: string, backgroundColor: string) {
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        px: 1.1,
        py: 0.35,
        borderRadius: 999,
        fontSize: 12,
        fontWeight: 800,
        color,
        backgroundColor,
        lineHeight: 1.2,
      }}
    >
      {label}
    </Box>
  );
}

function toCorrectBadge(correctYn: string | null | undefined) {
  const normalized = normalizeText(correctYn)?.toUpperCase();
  if (normalized === 'Y') {
    return badge('정답', '#137333', '#e8f5e9');
  }
  if (normalized === 'N') {
    return badge('오답', '#c62828', '#ffebee');
  }
  return badge('-', '#546e7a', '#eceff1');
}

function toStatusBadge(status: string | null | undefined) {
  const normalized = normalizeText(status)?.toUpperCase();
  if (normalized === 'SUCCESS') {
    return badge('SUCCESS', '#137333', '#e8f5e9');
  }
  if (normalized === 'FAILED') {
    return badge('FAILED', '#c62828', '#ffebee');
  }
  return badge(normalized ?? '-', '#455a64', '#eceff1');
}

type MetricCardProps = {
  title: string;
  metric: SupervisedMetric;
};

function MetricCard({ title, metric }: MetricCardProps) {
  const tone = metricTone(metric.value);
  const progressValue = metric.value == null ? 0 : Math.max(0, Math.min(metric.value * 100, 100));

  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent sx={{ py: 1.6 }}>
        <Typography variant="body2" sx={{ color: '#546e7a', fontWeight: 700 }}>
          {title}
        </Typography>
        <Typography variant="h4" sx={{ mt: 0.65, fontWeight: 800, color: tone.valueColor, letterSpacing: '-0.02em' }}>
          {formatPercent(metric.value)}
        </Typography>
        <Typography variant="caption" sx={{ display: 'block', mt: 0.8, color: '#607d8b', fontWeight: 700 }}>
          {formatFraction(metric.numerator, metric.denominator)}
        </Typography>
        <LinearProgress
          variant="determinate"
          value={progressValue}
          sx={{
            mt: 1.2,
            height: 7,
            borderRadius: 999,
            backgroundColor: tone.trackColor,
            '& .MuiLinearProgress-bar': {
              borderRadius: 999,
              backgroundColor: tone.barColor,
            },
          }}
        />
      </CardContent>
    </Card>
  );
}

type DistributionSegment = {
  label: string;
  color: string;
  count: number;
  ratio: number;
};

function buildDistributionSegments(items: SupervisedDistributionItem[]): DistributionSegment[] {
  const styleMap: Record<string, { label: string; color: string }> = {
    TP: { label: 'TP (True Positive)', color: '#3fbf7f' },
    TN: { label: 'TN (True Negative)', color: '#4f8ff7' },
    FP: { label: 'FP (False Positive)', color: '#f59f3a' },
    FN: { label: 'FN (False Negative)', color: '#e05a64' },
  };

  return items
    .map((item) => {
      const style = styleMap[item.errorType] ?? { label: item.errorType, color: '#90a4ae' };
      return {
        label: style.label,
        color: style.color,
        count: item.count,
        ratio: item.ratio,
      };
    })
    .filter((segment) => segment.count > 0);
}

function buildFeatureImportanceTop10(
  source: SupervisedFeatureImportance[] | null | undefined,
): SupervisedFeatureImportance[] {
  if (!source || source.length === 0) {
    return [];
  }

  const normalized = source
    .map((item) => ({
      rank: Number.isFinite(item.rank) ? item.rank : 0,
      feature: normalizeText(item.feature) ?? '',
      importance: Number.isFinite(item.importance) ? item.importance : 0,
    }))
    .filter((item) => item.feature.length > 0)
    .sort((left, right) => {
      const leftHasRank = left.rank > 0;
      const rightHasRank = right.rank > 0;
      if (leftHasRank && rightHasRank) {
        const rankCompare = left.rank - right.rank;
        if (rankCompare !== 0) {
          return rankCompare;
        }
      } else if (leftHasRank !== rightHasRank) {
        return leftHasRank ? -1 : 1;
      }

      const importanceCompare = right.importance - left.importance;
      if (importanceCompare !== 0) {
        return importanceCompare;
      }
      return left.feature.localeCompare(right.feature);
    })
    .slice(0, 10);

  return normalized.map((item, index) => ({
    rank: index + 1,
    feature: item.feature,
    importance: item.importance,
  }));
}

type FeatureImportanceChartProps = {
  rows: SupervisedFeatureImportance[];
};

function FeatureImportanceChart({ rows }: FeatureImportanceChartProps) {
  const maxImportance = rows.reduce((max, row) => Math.max(max, row.importance), 0);

  if (rows.length === 0) {
    return (
      <Alert severity="info" sx={{ mt: 1 }}>
        {NO_FEATURE_IMPORTANCE_MESSAGE}
      </Alert>
    );
  }

  return (
    <Stack spacing={1.05} sx={{ mt: 1.2 }}>
      {rows.map((row) => {
        const widthPercent = maxImportance > 0 ? (row.importance / maxImportance) * 100 : 0;
        return (
          <Box
            key={`${row.feature}-${row.rank}`}
            sx={{ display: 'grid', gridTemplateColumns: 'minmax(120px, 38%) 1fr 58px', gap: 1, alignItems: 'center' }}
          >
            <Typography
              variant="body2"
              noWrap
              title={row.feature}
              sx={{ color: '#455a64', fontWeight: 700 }}
            >
              {row.feature}
            </Typography>
            <Box sx={{ height: 12, borderRadius: 999, backgroundColor: '#edf2fb', border: '1px solid #dbe6f6' }}>
              <Box
                sx={{
                  width: `${Math.max(0, Math.min(widthPercent, 100))}%`,
                  height: '100%',
                  borderRadius: 999,
                  background: 'linear-gradient(90deg, #3b82f6 0%, #2563eb 100%)',
                  minWidth: widthPercent > 0 ? 4 : 0,
                }}
              />
            </Box>
            <Typography variant="caption" sx={{ textAlign: 'right', color: '#2f3e46', fontWeight: 700 }}>
              {formatImportance(row.importance)}
            </Typography>
          </Box>
        );
      })}
      <Typography variant="caption" sx={{ color: '#607d8b', fontWeight: 700, textAlign: 'right' }}>
        중요도 (Feature Importance)
      </Typography>
    </Stack>
  );
}

const TREND_SERIES = [
  { key: 'accuracy', label: 'Accuracy', color: '#f97316' },
  { key: 'precision', label: 'Precision', color: '#22c55e' },
  { key: 'recall', label: 'Recall', color: '#7c3aed' },
  { key: 'f1Score', label: 'F1 Score', color: '#3b82f6' },
] as const;

function normalizeTrendPoints(source: SupervisedTrendPoint[]): SupervisedTrendPoint[] {
  return [...source]
    .sort((left, right) => {
      const leftTime = left.regDate ? new Date(left.regDate).getTime() : 0;
      const rightTime = right.regDate ? new Date(right.regDate).getTime() : 0;
      return leftTime - rightTime;
    })
    .slice(-10);
}

type TrendChartProps = {
  points: SupervisedTrendPoint[];
};

function RunTrendChart({ points }: TrendChartProps) {
  if (points.length === 0) {
    return (
      <Alert severity="info" sx={{ mt: 1 }}>
        표시할 성능 추이 데이터가 없습니다.
      </Alert>
    );
  }

  const chartWidth = Math.max(760, points.length * 86);
  const chartHeight = 320;
  const margin = { top: 24, right: 24, bottom: 62, left: 48 };
  const bodyWidth = chartWidth - margin.left - margin.right;
  const bodyHeight = chartHeight - margin.top - margin.bottom;
  const xStep = points.length > 1 ? bodyWidth / (points.length - 1) : bodyWidth;
  const xForIndex = (index: number) => margin.left + (points.length > 1 ? index * xStep : bodyWidth / 2);
  const yForValue = (value: number) => margin.top + (1 - Math.max(0, Math.min(value, 100)) / 100) * bodyHeight;

  const yTicks = [0, 20, 40, 60, 80, 100];
  const labels = points.map((point) => {
    const label = formatAxisDateTime(point.regDate);
    if (label === '-') {
      return ['-', ''];
    }
    const splitIndex = label.indexOf(' ');
    if (splitIndex < 0) {
      return [label, ''];
    }
    return [label.slice(0, splitIndex), label.slice(splitIndex + 1)];
  });

  return (
    <Box sx={{ mt: 1, border: '1px solid #d7e0ee', borderRadius: 1, p: 1.2, overflowX: 'auto' }}>
      <Stack direction="row" spacing={1.5} sx={{ mb: 0.8, px: 0.5, flexWrap: 'wrap' }}>
        {TREND_SERIES.map((series) => (
          <Stack key={series.key} direction="row" spacing={0.6} alignItems="center">
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: series.color }} />
            <Typography variant="caption" sx={{ color: '#455a64', fontWeight: 700 }}>
              {series.label}
            </Typography>
          </Stack>
        ))}
      </Stack>

      <svg
        viewBox={`0 0 ${chartWidth} ${chartHeight}`}
        width="100%"
        style={{ minWidth: chartWidth, display: 'block' }}
        role="img"
        aria-label="Run metric trend chart"
      >
        {yTicks.map((tick) => {
          const y = yForValue(tick);
          return (
            <g key={`tick-${tick}`}>
              <line x1={margin.left} y1={y} x2={chartWidth - margin.right} y2={y} stroke="#edf1f7" strokeWidth={1} />
              <text x={margin.left - 8} y={y + 4} textAnchor="end" fontSize="11" fill="#708296">
                {tick}
              </text>
            </g>
          );
        })}

        {TREND_SERIES.map((series) => {
          const path = points
            .map((point, index) => {
              const rawValue = point[series.key];
              const percent = toPercent(rawValue);
              return `${index === 0 ? 'M' : 'L'} ${xForIndex(index)} ${yForValue(percent)}`;
            })
            .join(' ');
          return <path key={`path-${series.key}`} d={path} fill="none" stroke={series.color} strokeWidth={2.2} />;
        })}

        {points.map((point, index) => {
          const tooltip = [
            `run_id: ${point.runId}`,
            `실행 시각: ${formatDateTime(point.regDate)}`,
            `Accuracy: ${formatPercent(point.accuracy)}`,
            `Precision: ${formatPercent(point.precision)}`,
            `Recall: ${formatPercent(point.recall)}`,
            `F1 Score: ${formatPercent(point.f1Score)}`,
          ].join('\n');

          return TREND_SERIES.map((series) => {
            const value = toPercent(point[series.key]);
            return (
              <circle
                key={`point-${series.key}-${point.runId}-${index}`}
                cx={xForIndex(index)}
                cy={yForValue(value)}
                r={3.8}
                fill={series.color}
                stroke="#ffffff"
                strokeWidth={1.2}
              >
                <title>{tooltip}</title>
              </circle>
            );
          });
        })}

        {points.map((point, index) => (
          <g key={`x-label-${point.runId}-${index}`}>
            <text x={xForIndex(index)} y={chartHeight - 28} textAnchor="middle" fontSize="11" fill="#708296">
              {labels[index][0]}
            </text>
            <text x={xForIndex(index)} y={chartHeight - 14} textAnchor="middle" fontSize="11" fill="#708296">
              {labels[index][1]}
            </text>
          </g>
        ))}

        <text x={margin.left - 30} y={margin.top - 8} fontSize="11" fill="#708296">
          성능 (%)
        </text>
        <text x={chartWidth / 2} y={chartHeight - 2} textAnchor="middle" fontSize="11" fill="#708296">
          실행 일시
        </text>
      </svg>
    </Box>
  );
}

type ErrorTableProps = {
  title: string;
  rows: SupervisedErrorRow[];
  dangerTone?: boolean;
};

function ErrorTopTable({ title, rows, dangerTone = false }: ErrorTableProps) {
  return (
    <Card
      variant="outlined"
      sx={{
        height: '100%',
        borderColor: dangerTone ? '#f3c8cc' : '#d7e0ee',
        backgroundColor: dangerTone ? '#fffaf9' : '#ffffff',
      }}
    >
      <CardContent sx={{ py: 1.5 }}>
        <Typography variant="h6" sx={{ fontWeight: 800, color: dangerTone ? '#b71c1c' : '#102a43' }}>
          {title}
        </Typography>
        <TableContainer sx={{ border: '1px solid #d7e0ee', borderRadius: 1, mt: 1.3 }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 58, fontWeight: 800 }}>순위</TableCell>
                <TableCell sx={{ minWidth: 160, fontWeight: 800 }}>시간</TableCell>
                <TableCell sx={{ width: 84, fontWeight: 800 }}>실제</TableCell>
                <TableCell sx={{ width: 84, fontWeight: 800 }}>예측</TableCell>
                <TableCell sx={{ width: 100, fontWeight: 800 }}>확률(이상)</TableCell>
                <TableCell sx={{ minWidth: 200, fontWeight: 800 }}>주요 특성 Top 3</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 2.2, color: '#607d8b' }}>
                    데이터가 없습니다.
                  </TableCell>
                </TableRow>
              ) : (
                rows.map((row, index) => (
                  <TableRow key={`${title}-${row.timestamp ?? 'none'}-${index}`} hover>
                    <TableCell>{index + 1}</TableCell>
                    <TableCell>{formatDateTime(row.timestamp)}</TableCell>
                    <TableCell>{row.actualLabel ?? '-'}</TableCell>
                    <TableCell>{row.predictionLabel ?? '-'}</TableCell>
                    <TableCell>{formatProbability(row.probabilityAnomaly)}</TableCell>
                    <TableCell>{row.topFeatures.length > 0 ? row.topFeatures.join(', ') : '-'}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </CardContent>
    </Card>
  );
}

export function SupervisedResultPage() {
  const [runs, setRuns] = useState<SupervisedRun[]>([]);
  const [selectedRunId, setSelectedRunId] = useState('');
  const [triggerType, setTriggerType] = useState<'ALL' | 'MANUAL' | 'SCHEDULE'>('ALL');

  const [summary, setSummary] = useState<SupervisedSummary | null>(null);
  const [distribution, setDistribution] = useState<SupervisedDistribution>(EMPTY_DISTRIBUTION);
  const [errors, setErrors] = useState<SupervisedErrors>(EMPTY_ERRORS);
  const [trendPoints, setTrendPoints] = useState<SupervisedTrendPoint[]>([]);
  const [predictionPage, setPredictionPage] = useState<SupervisedPredictionPage>(EMPTY_PREDICTION_PAGE);

  const [loadingRuns, setLoadingRuns] = useState(false);
  const [loadingDashboard, setLoadingDashboard] = useState(false);
  const [loadingPredictions, setLoadingPredictions] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [distributionError, setDistributionError] = useState<string | null>(null);
  const [errorsError, setErrorsError] = useState<string | null>(null);
  const [trendError, setTrendError] = useState<string | null>(null);
  const [predictionError, setPredictionError] = useState<string | null>(null);
  const [lastFetchedAt, setLastFetchedAt] = useState<string | null>(null);

  const [predictionFilter, setPredictionFilter] = useState<'ALL' | 'CORRECT' | 'INCORRECT' | 'TP' | 'TN' | 'FP' | 'FN'>(
    'ALL',
  );
  const [fromDateTime, setFromDateTime] = useState('');
  const [toDateTime, setToDateTime] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(50);

  const loadRuns = useCallback(async () => {
    setLoadingRuns(true);
    setPageError(null);
    try {
      const response = await supervisedResultService.fetchRuns({
        triggerType,
        limit: 120,
      });
      setRuns(response);

      if (response.length === 0) {
        setSelectedRunId('');
        setSummary(null);
        setDistribution(EMPTY_DISTRIBUTION);
        setErrors(EMPTY_ERRORS);
        setTrendPoints([]);
        setPredictionPage(EMPTY_PREDICTION_PAGE);
        setDistributionError(null);
        setErrorsError(null);
        setTrendError(null);
        setPredictionError(null);
        return;
      }

      setSelectedRunId((previousRunId) => {
        if (previousRunId && response.some((run) => run.runId === previousRunId)) {
          return previousRunId;
        }
        return response[0].runId;
      });
    } catch (error: unknown) {
      setPageError(resolveErrorMessage(error, LOAD_ERROR_MESSAGE));
    } finally {
      setLoadingRuns(false);
    }
  }, [triggerType]);

  useEffect(() => {
    void loadRuns();
  }, [loadRuns]);

  useEffect(() => {
    if (!selectedRunId) {
      setSummary(null);
      setDistribution(EMPTY_DISTRIBUTION);
      setErrors(EMPTY_ERRORS);
      setTrendPoints([]);
      setDistributionError(null);
      setErrorsError(null);
      setTrendError(null);
      return;
    }

    let cancelled = false;
    const loadDashboard = async () => {
      setLoadingDashboard(true);
      setPageError(null);
      setDistributionError(null);
      setErrorsError(null);
      setTrendError(null);
      try {
        const [summaryResult, distributionResult, errorsResult, trendResult] = await Promise.allSettled([
          supervisedResultService.fetchSummary(selectedRunId),
          supervisedResultService.fetchDistribution(selectedRunId),
          supervisedResultService.fetchErrors(selectedRunId, 5),
          supervisedResultService.fetchTrend({ triggerType, limit: 10 }),
        ]);
        if (cancelled) {
          return;
        }

        if (summaryResult.status === 'fulfilled') {
          setSummary(summaryResult.value);
        } else {
          setSummary(null);
          setPageError(resolveErrorMessage(summaryResult.reason, LOAD_ERROR_MESSAGE));
        }

        if (distributionResult.status === 'fulfilled') {
          setDistribution(distributionResult.value);
        } else {
          setDistribution(EMPTY_DISTRIBUTION);
          setDistributionError(resolveErrorMessage(distributionResult.reason, LOAD_ERROR_MESSAGE));
        }

        if (errorsResult.status === 'fulfilled') {
          setErrors(errorsResult.value);
        } else {
          setErrors(EMPTY_ERRORS);
          setErrorsError(resolveErrorMessage(errorsResult.reason, LOAD_ERROR_MESSAGE));
        }

        if (trendResult.status === 'fulfilled') {
          setTrendPoints(normalizeTrendPoints(trendResult.value));
        } else {
          setTrendPoints([]);
          setTrendError(resolveErrorMessage(trendResult.reason, LOAD_ERROR_MESSAGE));
        }

        if (
          summaryResult.status === 'fulfilled' ||
          distributionResult.status === 'fulfilled' ||
          errorsResult.status === 'fulfilled' ||
          trendResult.status === 'fulfilled'
        ) {
          setLastFetchedAt(new Date().toISOString());
        }
      } catch (error: unknown) {
        if (!cancelled) {
          setPageError(resolveErrorMessage(error, LOAD_ERROR_MESSAGE));
        }
      } finally {
        if (!cancelled) {
          setLoadingDashboard(false);
        }
      }
    };

    void loadDashboard();
    return () => {
      cancelled = true;
    };
  }, [selectedRunId, triggerType]);

  useEffect(() => {
    if (!selectedRunId) {
      setPredictionPage(EMPTY_PREDICTION_PAGE);
      setPredictionError(null);
      return;
    }

    let cancelled = false;
    const loadPredictions = async () => {
      setLoadingPredictions(true);
      setPredictionError(null);
      try {
        const predictions = await supervisedResultService.fetchPredictions({
          runId: selectedRunId,
          filter: predictionFilter,
          from: toUtcIso(fromDateTime),
          to: toUtcIso(toDateTime),
          page,
          size: rowsPerPage,
        });
        if (!cancelled) {
          setPredictionPage(predictions);
        }
      } catch (error: unknown) {
        if (!cancelled) {
          setPredictionPage(EMPTY_PREDICTION_PAGE);
          setPredictionError(resolveErrorMessage(error, LOAD_ERROR_MESSAGE));
        }
      } finally {
        if (!cancelled) {
          setLoadingPredictions(false);
        }
      }
    };

    void loadPredictions();
    return () => {
      cancelled = true;
    };
  }, [selectedRunId, predictionFilter, fromDateTime, toDateTime, page, rowsPerPage]);

  const selectedRun = useMemo(
    () => runs.find((run) => run.runId === selectedRunId) ?? null,
    [runs, selectedRunId],
  );

  const metrics = useMemo(() => {
    if (!summary) {
      return [
        { title: 'Accuracy', metric: { ...EMPTY_METRIC, key: 'accuracy', label: 'Accuracy' } },
        { title: 'Precision', metric: { ...EMPTY_METRIC, key: 'precision', label: 'Precision' } },
        { title: 'Recall', metric: { ...EMPTY_METRIC, key: 'recall', label: 'Recall' } },
        { title: 'F1 Score', metric: { ...EMPTY_METRIC, key: 'f1Score', label: 'F1 Score' } },
      ] as const;
    }
    return [
      { title: 'Accuracy', metric: summary.accuracy },
      { title: 'Precision', metric: summary.precision },
      { title: 'Recall', metric: summary.recall },
      { title: 'F1 Score', metric: summary.f1Score },
    ] as const;
  }, [summary]);

  const distributionSegments = useMemo(
    () => buildDistributionSegments(distribution.items),
    [distribution.items],
  );
  const featureImportanceTop10 = useMemo(
    () => buildFeatureImportanceTop10(summary?.featureImportances),
    [summary?.featureImportances],
  );

  const loadingAny = loadingRuns || loadingDashboard;
  const infoStatus = summary?.status ?? selectedRun?.status ?? null;
  const infoAlgoCode = summary?.algoCode ?? selectedRun?.algoCode ?? '-';
  const infoDatasetKey = summary?.datasetKey ?? selectedRun?.datasetKey ?? '-';
  const infoExecutedAt = summary?.executedAt ?? selectedRun?.executedAt ?? null;
  const infoPredictionCount = summary?.totalPredictions ?? selectedRun?.totalPredictions ?? 0;

  const handleRefresh = useCallback(async () => {
    setPage(0);
    await loadRuns();
  }, [loadRuns]);

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 800, color: '#102a43' }}>
          지도학습 분석 결과
        </Typography>
        <Typography variant="body1" sx={{ color: '#607d8b', mt: 0.45 }}>
          Random Forest 모델의 지도학습 예측 성능을 운영 화면에서 확인합니다.
        </Typography>
      </Box>

      {pageError ? <Alert severity="error">{pageError}</Alert> : null}
      {loadingRuns ? (
        <Stack direction="row" spacing={1.2} alignItems="center">
          <CircularProgress size={18} />
          <Typography variant="body2" sx={{ color: '#607d8b' }}>
            실행 목록을 불러오는 중입니다...
          </Typography>
        </Stack>
      ) : null}

      {!loadingRuns && runs.length === 0 ? <Alert severity="info">{NO_RUNS_MESSAGE}</Alert> : null}

      <Card variant="outlined" sx={{ borderColor: '#d6e0ef' }}>
        <CardContent sx={{ py: 1.9 }}>
          <Stack spacing={1.4}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.2} alignItems={{ xs: 'stretch', md: 'center' }}>
              <TextField
                select
                fullWidth
                size="small"
                label="실행 Run 선택"
                value={selectedRunId}
                onChange={(event) => {
                  setPage(0);
                  setSelectedRunId(event.target.value);
                }}
                disabled={runs.length === 0}
                sx={{ maxWidth: { xs: '100%', md: 540 } }}
              >
                {runs.map((run) => (
                  <MenuItem key={run.runId} value={run.runId}>
                    {run.runId} | {formatDateTime(run.executedAt)}
                  </MenuItem>
                ))}
              </TextField>

              <TextField
                select
                size="small"
                label="실행 유형"
                value={triggerType}
                onChange={(event) => {
                  const nextTrigger = event.target.value as 'ALL' | 'MANUAL' | 'SCHEDULE';
                  setTriggerType(nextTrigger);
                  setPage(0);
                }}
                sx={{ width: { xs: '100%', md: 170 } }}
              >
                {TRIGGER_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </TextField>

              <Button
                variant="outlined"
                startIcon={<RefreshRoundedIcon />}
                onClick={() => {
                  void handleRefresh();
                }}
                disabled={loadingAny}
              >
                새로고침
              </Button>
            </Stack>

            <Divider />

            <Box
              sx={{
                display: 'grid',
                gap: 1.2,
                gridTemplateColumns: {
                  xs: '1fr',
                  sm: 'repeat(2, minmax(0, 1fr))',
                  lg: '220px 160px 220px 220px 220px',
                },
              }}
            >
              <Box>
                <Typography variant="caption" sx={{ color: '#607d8b' }}>
                  최신 실행 상태
                </Typography>
                <Box sx={{ mt: 0.35 }}>{toStatusBadge(infoStatus)}</Box>
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: '#607d8b' }}>
                  알고리즘
                </Typography>
                <Typography variant="body2" sx={{ mt: 0.35, fontWeight: 800 }}>
                  {infoAlgoCode ?? '-'}
                </Typography>
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: '#607d8b' }}>
                  데이터셋
                </Typography>
                <Typography variant="body2" sx={{ mt: 0.35, fontWeight: 800 }}>
                  {infoDatasetKey ?? '-'}
                </Typography>
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: '#607d8b' }}>
                  실행 시각
                </Typography>
                <Typography variant="body2" sx={{ mt: 0.35, fontWeight: 800 }}>
                  {formatDateTime(infoExecutedAt)}
                </Typography>
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: '#607d8b' }}>
                  총 예측 건수
                </Typography>
                <Typography variant="body2" sx={{ mt: 0.35, fontWeight: 800 }}>
                  {formatNumber(infoPredictionCount)}건
                </Typography>
              </Box>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      <Box
        sx={{
          display: 'grid',
          gap: 1.4,
          gridTemplateColumns: { xs: '1fr', lg: 'repeat(4, minmax(0, 1fr))' },
        }}
      >
        {metrics.map((metricItem) => (
          <MetricCard key={metricItem.title} title={metricItem.title} metric={metricItem.metric} />
        ))}
      </Box>

      <Box sx={{ display: 'grid', gap: 1.4, gridTemplateColumns: { xs: '1fr', xl: '1.3fr 1fr' } }}>
        <Card variant="outlined">
          <CardContent sx={{ py: 1.5 }}>
            <Typography variant="h6" sx={{ fontWeight: 800, color: '#102a43' }}>
              Confusion Matrix
            </Typography>
            <TableContainer sx={{ border: '1px solid #d7e0ee', borderRadius: 1, mt: 1.3 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell />
                    <TableCell align="center" sx={{ fontWeight: 800 }}>
                      예측 정상(0)
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 800 }}>
                      예측 이상(1)
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 800 }}>실제 정상(0)</TableCell>
                    <TableCell align="center" sx={{ backgroundColor: '#e8f5e9', fontWeight: 800 }}>
                      TN {formatNumber(summary?.tn ?? 0)}
                    </TableCell>
                    <TableCell align="center" sx={{ backgroundColor: '#fff3e0', fontWeight: 800 }}>
                      FP {formatNumber(summary?.fp ?? 0)}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 800 }}>실제 이상(1)</TableCell>
                    <TableCell align="center" sx={{ backgroundColor: '#ffebee', fontWeight: 800, color: '#b71c1c' }}>
                      FN {formatNumber(summary?.fn ?? 0)}
                    </TableCell>
                    <TableCell align="center" sx={{ backgroundColor: '#e8f5e9', fontWeight: 800 }}>
                      TP {formatNumber(summary?.tp ?? 0)}
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent sx={{ py: 1.5 }}>
            <Typography variant="h6" sx={{ fontWeight: 800, color: '#102a43' }}>
              예측 결과 분포
            </Typography>
            {distributionError ? (
              <Alert severity="warning" sx={{ mt: 1 }}>
                {distributionError}
              </Alert>
            ) : null}

            <Box sx={{ display: 'grid', gap: 1.2, gridTemplateColumns: { xs: '1fr', md: '270px 1fr' }, mt: 1 }}>
              <Box sx={{ position: 'relative', width: 260, height: 260, mx: 'auto' }}>
                <svg width="260" height="260" viewBox="0 0 260 260" aria-label="Prediction distribution donut chart">
                  <circle cx="130" cy="130" r="90" stroke="#e8edf5" strokeWidth="30" fill="none" />
                  {(() => {
                    const circumference = 2 * Math.PI * 90;
                    let cumulative = 0;
                    return distributionSegments.map((segment) => {
                      const dash = Math.max(segment.ratio * circumference, 0);
                      const dashOffset = circumference * (1 - cumulative);
                      cumulative += segment.ratio;
                      return (
                        <circle
                          key={`donut-${segment.label}`}
                          cx="130"
                          cy="130"
                          r="90"
                          stroke={segment.color}
                          strokeWidth="30"
                          fill="none"
                          strokeDasharray={`${dash} ${circumference - dash}`}
                          strokeDashoffset={dashOffset}
                          transform="rotate(-90 130 130)"
                          strokeLinecap="butt"
                        />
                      );
                    });
                  })()}
                </svg>
                <Box
                  sx={{
                    position: 'absolute',
                    inset: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Typography variant="caption" sx={{ color: '#607d8b', fontWeight: 700 }}>
                    총 예측 건수
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 800 }}>
                    {formatNumber(distribution.totalCount)}
                  </Typography>
                </Box>
              </Box>

              <Stack spacing={0.9} sx={{ alignSelf: 'center' }}>
                {(distributionSegments.length === 0 ? buildDistributionSegments(distribution.items) : distributionSegments).map(
                  (segment) => (
                    <Stack key={segment.label} direction="row" spacing={1} alignItems="center">
                      <Box sx={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: segment.color }} />
                      <Typography variant="body2" sx={{ flex: 1, fontWeight: 700 }}>
                        {segment.label}
                      </Typography>
                      <Typography variant="body2" sx={{ color: '#455a64', fontWeight: 700 }}>
                        {formatNumber(segment.count)} ({(segment.ratio * 100).toFixed(2)}%)
                      </Typography>
                    </Stack>
                  ),
                )}
              </Stack>
            </Box>
          </CardContent>
        </Card>
      </Box>

      <Box sx={{ display: 'grid', gap: 1.4, gridTemplateColumns: { xs: '1fr', xl: '1fr 1fr' } }}>
        <Card variant="outlined">
          <CardContent sx={{ py: 1.5 }}>
            <Typography variant="h6" sx={{ fontWeight: 800, color: '#102a43' }}>
              Feature Importance Top 10
            </Typography>
            <FeatureImportanceChart rows={featureImportanceTop10} />
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent sx={{ py: 1.5 }}>
            <Typography variant="h6" sx={{ fontWeight: 800, color: '#102a43' }}>
              Run별 성능 추이
            </Typography>
            {trendError ? (
              <Alert severity="warning" sx={{ mt: 1 }}>
                {trendError}
              </Alert>
            ) : null}
            <RunTrendChart points={trendPoints} />
          </CardContent>
        </Card>
      </Box>

      {errorsError ? <Alert severity="warning">{errorsError}</Alert> : null}
      <Box sx={{ display: 'grid', gap: 1.4, gridTemplateColumns: { xs: '1fr', xl: '1fr 1fr' } }}>
        <ErrorTopTable title="오탐 (False Positive) TOP 5" rows={errors.fpTop} />
        <ErrorTopTable title="미탐 (False Negative) TOP 5" rows={errors.fnTop} dangerTone />
      </Box>

      <Card variant="outlined">
        <CardContent sx={{ py: 1.6 }}>
          <Stack spacing={1.3}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.1} alignItems={{ xs: 'stretch', md: 'center' }}>
              <Typography variant="h6" sx={{ fontWeight: 800, color: '#102a43', flex: 1 }}>
                예측 결과 (Prediction Result)
              </Typography>
              <TextField
                select
                size="small"
                label="필터"
                value={predictionFilter}
                onChange={(event) => {
                  setPage(0);
                  setPredictionFilter(
                    event.target.value as 'ALL' | 'CORRECT' | 'INCORRECT' | 'TP' | 'TN' | 'FP' | 'FN',
                  );
                }}
                sx={{ width: { xs: '100%', md: 170 } }}
              >
                {PREDICTION_FILTER_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                type="datetime-local"
                size="small"
                label="시작 시각"
                InputLabelProps={{ shrink: true }}
                value={fromDateTime}
                onChange={(event) => {
                  setPage(0);
                  setFromDateTime(event.target.value);
                }}
                sx={{ width: { xs: '100%', md: 210 } }}
              />
              <TextField
                type="datetime-local"
                size="small"
                label="종료 시각"
                InputLabelProps={{ shrink: true }}
                value={toDateTime}
                onChange={(event) => {
                  setPage(0);
                  setToDateTime(event.target.value);
                }}
                sx={{ width: { xs: '100%', md: 210 } }}
              />
            </Stack>
            {predictionError ? <Alert severity="error">{predictionError}</Alert> : null}

            <TableContainer sx={{ border: '1px solid #d7e0ee', borderRadius: 1, maxHeight: 420 }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ width: 72, fontWeight: 800 }}>#</TableCell>
                    <TableCell sx={{ minWidth: 170, fontWeight: 800 }}>시간</TableCell>
                    <TableCell sx={{ width: 90, fontWeight: 800 }}>실제 라벨</TableCell>
                    <TableCell sx={{ width: 90, fontWeight: 800 }}>예측 라벨</TableCell>
                    <TableCell sx={{ width: 110, fontWeight: 800 }}>확률(이상)</TableCell>
                    <TableCell sx={{ width: 110, fontWeight: 800 }}>확률(정상)</TableCell>
                    <TableCell sx={{ width: 92, fontWeight: 800 }}>정답 여부</TableCell>
                    <TableCell sx={{ width: 80, fontWeight: 800 }}>오류 유형</TableCell>
                    <TableCell sx={{ minWidth: 220, fontWeight: 800 }}>주요 특성 Top 3</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {predictionPage.items.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={9} align="center" sx={{ py: 2.5, color: '#607d8b' }}>
                        {loadingPredictions ? '예측 결과를 불러오는 중입니다...' : NO_PREDICTION_MESSAGE}
                      </TableCell>
                    </TableRow>
                  ) : (
                    predictionPage.items.map((row: SupervisedPredictionRow, index) => (
                      <TableRow
                        key={`${row.timestamp ?? 'none'}-${index}`}
                        hover
                        sx={{
                          '& td': {
                            backgroundColor: row.errorType === 'FN' ? '#fff8f8' : undefined,
                          },
                        }}
                      >
                        <TableCell>{page * rowsPerPage + index + 1}</TableCell>
                        <TableCell>{formatDateTime(row.timestamp)}</TableCell>
                        <TableCell>{row.actualLabel ?? '-'}</TableCell>
                        <TableCell>{row.predictionLabel ?? '-'}</TableCell>
                        <TableCell>{formatProbability(row.probabilityAnomaly)}</TableCell>
                        <TableCell>{formatProbability(row.probabilityNormal)}</TableCell>
                        <TableCell>{toCorrectBadge(row.correctYn)}</TableCell>
                        <TableCell>{row.errorType ?? '-'}</TableCell>
                        <TableCell>{row.topFeatures.length > 0 ? row.topFeatures.join(', ') : '-'}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </TableContainer>

            <TablePagination
              component="div"
              count={predictionPage.total}
              page={page}
              onPageChange={(_event, nextPage) => {
                setPage(nextPage);
              }}
              rowsPerPage={rowsPerPage}
              onRowsPerPageChange={(event) => {
                const nextSize = Number.parseInt(event.target.value, 10);
                setRowsPerPage(nextSize);
                setPage(0);
              }}
              rowsPerPageOptions={[20, 50, 100, 200]}
              labelRowsPerPage="페이지 크기"
              labelDisplayedRows={({ from, to, count }) => `${from}-${to} / ${count.toLocaleString('ko-KR')}건`}
            />
          </Stack>
        </CardContent>
      </Card>

      <Typography variant="caption" sx={{ color: '#607d8b' }}>
        마지막 갱신: {formatDateTime(lastFetchedAt)}
      </Typography>
    </Stack>
  );
}
