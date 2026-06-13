import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { healthIndexService } from '../services/healthIndexService';
import type { HealthIndexPoint, HealthIndexRun, HealthIndexSummary, HealthIndexTrendResponse } from '../types/healthIndex';

const LOAD_ERROR_MESSAGE = 'Health Index 데이터를 불러오지 못했습니다.';
const NO_RUNS_MESSAGE = '실행 이력이 없습니다. 먼저 AI 모델 학습 또는 이상탐지를 실행해 주세요.';
const NO_TREND_MESSAGE = '조회 조건에 해당하는 Health Index 데이터가 없습니다.';

const STATUS_OPTIONS = [
  { value: 'ALL', label: '전체' },
  { value: 'NORMAL', label: 'NORMAL' },
  { value: 'WARNING', label: 'WARNING' },
  { value: 'CRITICAL', label: 'CRITICAL' },
] as const;

const EMPTY_SUMMARY: HealthIndexSummary = {
  latestHealthIndex: null,
  latestHealthIndexPercent: null,
  avgHealthIndexPercent: null,
  minHealthIndexPercent: null,
  maxHealthIndexPercent: null,
  latestStatus: null,
  normalCount: 0,
  warningCount: 0,
  criticalCount: 0,
  totalCount: 0,
  latestWindowStart: null,
  latestWindowEnd: null,
};

const CHART_WIDTH = 980;
const CHART_HEIGHT = 280;
const CHART_MARGIN = { top: 12, right: 14, bottom: 36, left: 50 };
const CHART_PLOT_WIDTH = CHART_WIDTH - CHART_MARGIN.left - CHART_MARGIN.right;
const CHART_PLOT_HEIGHT = CHART_HEIGHT - CHART_MARGIN.top - CHART_MARGIN.bottom;

type ChartBar = {
  x: number;
  y: number;
  width: number;
  height: number;
  row: HealthIndexPoint;
  percent: number;
};

function normalizeText(value: string | null | undefined): string | null {
  const text = value?.trim();
  if (!text) {
    return null;
  }
  return text;
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
  return `${value.toFixed(1)}%`;
}

function formatAnomalyScore(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '-';
  }
  return value.toFixed(4);
}

function formatCount(value: number | null | undefined): string {
  return `${value ?? 0}건`;
}

function toUtcIso(datetimeLocal: string): string | null {
  const text = datetimeLocal.trim();
  if (!text) {
    return null;
  }
  const parsed = new Date(text);
  if (Number.isNaN(parsed.getTime())) {
    return text;
  }
  return parsed.toISOString();
}

function statusStyle(status: string | null | undefined): { label: string; color: string; backgroundColor: string } {
  const normalized = normalizeText(status)?.toUpperCase();
  if (normalized === 'NORMAL') {
    return { label: 'NORMAL', color: '#1b5e20', backgroundColor: '#e8f5e9' };
  }
  if (normalized === 'WARNING') {
    return { label: 'WARNING', color: '#a84300', backgroundColor: '#fff3e0' };
  }
  if (normalized === 'CRITICAL') {
    return { label: 'CRITICAL', color: '#b71c1c', backgroundColor: '#ffebee' };
  }
  return { label: normalized ?? '-', color: '#455a64', backgroundColor: '#eceff1' };
}

function statusPointColor(status: string | null | undefined): string {
  const normalized = normalizeText(status)?.toUpperCase();
  if (normalized === 'NORMAL') {
    return '#2e7d32';
  }
  if (normalized === 'WARNING') {
    return '#ef6c00';
  }
  if (normalized === 'CRITICAL') {
    return '#c62828';
  }
  return '#1565c0';
}

function buildRunLabel(run: HealthIndexRun): string {
  const algoLabel = normalizeText(run.algoName) ?? normalizeText(run.algoCode) ?? 'Unknown';
  return `${run.runId} | ${formatDateTime(run.executedAt)} | ${algoLabel}`;
}

function sampleRowsForChart(rows: HealthIndexPoint[], maxBars = 180): HealthIndexPoint[] {
  if (rows.length <= maxBars) {
    return rows;
  }

  const step = Math.ceil(rows.length / maxBars);
  return rows.filter((_, index) => index % step === 0);
}

function buildChartBars(rows: HealthIndexPoint[]): ChartBar[] {
  if (rows.length === 0) {
    return [];
  }

  const gap = rows.length > 80 ? 1 : 3;
  const slotWidth = CHART_PLOT_WIDTH / rows.length;
  const barWidth = Math.max(2, Math.min(18, slotWidth - gap));

  return rows.map((row, index) => {
    const percent = Math.min(Math.max(row.healthIndexPercent as number, 0), 100);
    const height = (percent / 100) * CHART_PLOT_HEIGHT;
    const x = CHART_MARGIN.left + index * slotWidth + (slotWidth - barWidth) / 2;
    const y = CHART_MARGIN.top + CHART_PLOT_HEIGHT - height;
    return { x, y, width: barWidth, height, row, percent };
  });
}

export function HealthIndexPage() {
  const [runs, setRuns] = useState<HealthIndexRun[]>([]);
  const [trendData, setTrendData] = useState<HealthIndexTrendResponse | null>(null);
  const [loadingRuns, setLoadingRuns] = useState(false);
  const [loadingTrend, setLoadingTrend] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [lastFetchedAt, setLastFetchedAt] = useState<string | null>(null);

  const [selectedAlgoCode, setSelectedAlgoCode] = useState('ALL');
  const [selectedRunId, setSelectedRunId] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [fromDateTime, setFromDateTime] = useState('');
  const [toDateTime, setToDateTime] = useState('');

  const algorithmOptions = useMemo(() => {
    const uniqueAlgorithms = new Map<string, { code: string; name: string }>();
    for (const run of runs) {
      const code = normalizeText(run.algoCode);
      if (!code || uniqueAlgorithms.has(code)) {
        continue;
      }
      uniqueAlgorithms.set(code, {
        code,
        name: normalizeText(run.algoName) ?? code,
      });
    }
    return Array.from(uniqueAlgorithms.values());
  }, [runs]);

  const filteredRuns = useMemo(() => {
    if (selectedAlgoCode === 'ALL') {
      return runs;
    }
    return runs.filter((run) => normalizeText(run.algoCode) === selectedAlgoCode);
  }, [runs, selectedAlgoCode]);

  const selectedRun = useMemo(
    () => filteredRuns.find((run) => run.runId === selectedRunId) ?? null,
    [filteredRuns, selectedRunId],
  );

  const summary = trendData?.summary ?? EMPTY_SUMMARY;

  const chartRows = useMemo(() => {
    const sourceRows = trendData?.points ?? [];
    return sourceRows.filter(
      (row): row is HealthIndexPoint & { healthIndexPercent: number } =>
        typeof row.healthIndexPercent === 'number' && Number.isFinite(row.healthIndexPercent),
    );
  }, [trendData]);

  const sampledChartRows = useMemo(() => sampleRowsForChart(chartRows), [chartRows]);
  const chartBars = useMemo(() => buildChartBars(sampledChartRows), [sampledChartRows]);
  const chartLinePath = useMemo(() => {
    if (chartBars.length === 0) {
      return '';
    }
    return chartBars
      .map((bar, index) => {
        const x = bar.x + bar.width / 2;
        const y = bar.y;
        return `${index === 0 ? 'M' : 'L'} ${x} ${y}`;
      })
      .join(' ');
  }, [chartBars]);

  const tableRows = useMemo(() => {
    const sourceRows = trendData?.points ?? [];
    return [...sourceRows].reverse();
  }, [trendData]);

  const loadRuns = useCallback(async () => {
    setLoadingRuns(true);
    setPageError(null);
    try {
      const response = await healthIndexService.fetchHealthIndexRuns();
      setRuns(response);
      setLastFetchedAt(new Date().toISOString());
    } catch (error: unknown) {
      setRuns([]);
      setTrendData(null);
      setPageError(error instanceof Error ? error.message : LOAD_ERROR_MESSAGE);
    } finally {
      setLoadingRuns(false);
    }
  }, []);

  const loadTrend = useCallback(
    async (targetRun: HealthIndexRun, status: string, from: string, to: string) => {
      if (!targetRun.datasetKey) {
        setTrendData(null);
        return;
      }

      setLoadingTrend(true);
      setPageError(null);

      try {
        const response = await healthIndexService.fetchHealthIndexTrend({
          runId: targetRun.runId,
          datasetKey: targetRun.datasetKey,
          status: status === 'ALL' ? null : status,
          from: toUtcIso(from),
          to: toUtcIso(to),
        });
        setTrendData(response);
        setLastFetchedAt(new Date().toISOString());
      } catch (error: unknown) {
        setTrendData(null);
        setPageError(error instanceof Error ? error.message : LOAD_ERROR_MESSAGE);
      } finally {
        setLoadingTrend(false);
      }
    },
    [],
  );

  useEffect(() => {
    void loadRuns();
  }, [loadRuns]);

  useEffect(() => {
    if (algorithmOptions.length === 0) {
      setSelectedAlgoCode('ALL');
      return;
    }
    const exists = selectedAlgoCode !== 'ALL' && algorithmOptions.some((item) => item.code === selectedAlgoCode);
    if (!exists && selectedAlgoCode !== 'ALL') {
      setSelectedAlgoCode('ALL');
    }
  }, [algorithmOptions, selectedAlgoCode]);

  useEffect(() => {
    if (filteredRuns.length === 0) {
      setSelectedRunId('');
      setTrendData(null);
      return;
    }

    const hasSelected = filteredRuns.some((run) => run.runId === selectedRunId);
    if (!hasSelected) {
      setSelectedRunId(filteredRuns[0].runId);
    }
  }, [filteredRuns, selectedRunId]);

  useEffect(() => {
    if (!selectedRun || !selectedRun.datasetKey) {
      setTrendData(null);
      return;
    }
    void loadTrend(selectedRun, statusFilter, fromDateTime, toDateTime);
  }, [selectedRun?.runId, selectedRun?.datasetKey, loadTrend]);

  const handleSearch = async () => {
    if (!selectedRun) {
      return;
    }
    await loadTrend(selectedRun, statusFilter, fromDateTime, toDateTime);
  };

  const handleRefresh = async () => {
    await loadRuns();
    if (selectedRun) {
      await loadTrend(selectedRun, statusFilter, fromDateTime, toDateTime);
    }
  };

  return (
    <Stack spacing={1.6} sx={{ pb: 2 }}>
      <Card variant="outlined" sx={{ borderColor: '#d6e0ef' }}>
        <CardContent sx={{ py: 2.2 }}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            spacing={1.2}
          >
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.6 }}>
                Health Index 산출
              </Typography>
              <Typography variant="body1" color="text.secondary" sx={{ mt: 0.45 }}>
                이상탐지 결과를 기반으로 설비 상태 점수의 흐름을 확인합니다.
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.15 }}>
                Health Index는 높을수록 정상 상태에 가깝고, 낮을수록 상태 저하 가능성이 큽니다.
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.75, display: 'block' }}>
                마지막 조회 시각: {formatDateTime(lastFetchedAt)}
              </Typography>
            </Box>

            <Button
              variant="outlined"
              startIcon={<RefreshRoundedIcon />}
              onClick={() => void handleRefresh()}
              disabled={loadingRuns || loadingTrend}
              sx={{ minWidth: 128, fontWeight: 700 }}
            >
              {loadingRuns ? '새로고침 중...' : '새로고침'}
            </Button>
          </Stack>
        </CardContent>
      </Card>

      {pageError ? <Alert severity="error">{LOAD_ERROR_MESSAGE}</Alert> : null}

      {!loadingRuns && runs.length === 0 ? <Alert severity="info">{NO_RUNS_MESSAGE}</Alert> : null}

      <Card variant="outlined">
        <CardContent sx={{ py: 1.7 }}>
          <Typography variant="subtitle1" fontWeight={800}>
            조회 조건
          </Typography>
          <Divider sx={{ my: 1 }} />

          <Box
            sx={{
              display: 'grid',
              gap: 1,
              gridTemplateColumns: { xs: '1fr', lg: '170px minmax(260px, 1fr) 190px 190px 160px 112px' },
              alignItems: 'center',
            }}
          >
            <TextField
              select
              size="small"
              label="알고리즘 선택"
              value={selectedAlgoCode}
              onChange={(event) => {
                setSelectedAlgoCode(event.target.value);
                setSelectedRunId('');
              }}
              disabled={loadingRuns || runs.length === 0}
            >
              <MenuItem value="ALL">전체</MenuItem>
              {algorithmOptions.map((algorithm) => (
                <MenuItem key={algorithm.code} value={algorithm.code}>
                  {algorithm.name}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              size="small"
              label="실행 데이터 선택"
              value={selectedRunId}
              onChange={(event) => setSelectedRunId(event.target.value)}
              disabled={loadingRuns || filteredRuns.length === 0}
            >
              {filteredRuns.map((runOption) => (
                <MenuItem key={runOption.runId} value={runOption.runId}>
                  {buildRunLabel(runOption)}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              size="small"
              label="기간 시작"
              type="datetime-local"
              value={fromDateTime}
              onChange={(event) => setFromDateTime(event.target.value)}
              InputLabelProps={{ shrink: true }}
            />

            <TextField
              size="small"
              label="기간 종료"
              type="datetime-local"
              value={toDateTime}
              onChange={(event) => setToDateTime(event.target.value)}
              InputLabelProps={{ shrink: true }}
            />

            <TextField
              select
              size="small"
              label="상태 선택"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              {STATUS_OPTIONS.map((statusOption) => (
                <MenuItem key={statusOption.value} value={statusOption.value}>
                  {statusOption.label}
                </MenuItem>
              ))}
            </TextField>

            <Button
              variant="contained"
              startIcon={<SearchRoundedIcon />}
              onClick={() => void handleSearch()}
              disabled={!selectedRun || loadingTrend}
              sx={{ minHeight: 40, fontWeight: 800 }}
            >
              조회
            </Button>
          </Box>

          <Typography variant="body2" color="text.secondary" sx={{ mt: 1.05 }}>
            선택 실행: {selectedRun?.runId ?? '-'} | 데이터셋: {selectedRun?.datasetKey ?? '-'} | 기준:
            thisanomalyresult.health_index
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ mt: 0.25, display: 'block' }}>
            상태 구분은 현재 이상탐지 결과의 status 값을 기준으로 표시됩니다.
          </Typography>
        </CardContent>
      </Card>

      <Box
        sx={{
          display: 'grid',
          gap: 1.6,
          gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' },
          alignItems: 'stretch',
        }}
      >
        <Card variant="outlined">
          <CardContent sx={{ py: 1.5 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} spacing={0.6}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  Health Index 추이
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  막대는 Health Index 값, 선은 추세, 색상은 현재 status 기준으로 표시됩니다.
                </Typography>
              </Box>
              {chartRows.length > sampledChartRows.length ? (
                <Typography variant="caption" color="text.secondary">
                  표시 샘플 {sampledChartRows.length.toLocaleString()} / 전체 {chartRows.length.toLocaleString()}건
                </Typography>
              ) : null}
            </Stack>
            <Divider sx={{ my: 1 }} />

            {loadingTrend ? (
              <Stack direction="row" spacing={1} alignItems="center" sx={{ py: 2 }}>
                <CircularProgress size={20} />
                <Typography variant="body2" color="text.secondary">
                  Health Index 추이를 불러오는 중입니다.
                </Typography>
              </Stack>
            ) : null}

            {!loadingTrend && trendData && chartRows.length === 0 ? (
              <Alert severity="info">{NO_TREND_MESSAGE}</Alert>
            ) : null}

            {!loadingTrend && chartRows.length > 0 ? (
              <Box
                sx={{
                  width: '100%',
                  border: '1px solid #d7e0ee',
                  borderRadius: 1,
                  p: 1,
                  backgroundColor: '#fff',
                  overflowX: 'auto',
                }}
              >
                <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} width="100%" role="img" aria-label="Health Index bar and trend line chart">
                  {[0, 25, 50, 75, 100].map((tick) => {
                    const y = CHART_MARGIN.top + (1 - tick / 100) * CHART_PLOT_HEIGHT;
                    return (
                      <g key={`tick-${tick}`}>
                        <line
                          x1={CHART_MARGIN.left}
                          y1={y}
                          x2={CHART_WIDTH - CHART_MARGIN.right}
                          y2={y}
                          stroke="#e8ecf1"
                          strokeWidth="1"
                        />
                        <text x={CHART_MARGIN.left - 8} y={y + 4} textAnchor="end" fontSize="11" fill="#8896a8">
                          {tick}
                        </text>
                      </g>
                    );
                  })}

                  <line
                    x1={CHART_MARGIN.left}
                    y1={CHART_MARGIN.top}
                    x2={CHART_MARGIN.left}
                    y2={CHART_MARGIN.top + CHART_PLOT_HEIGHT}
                    stroke="#9aa9bc"
                    strokeWidth="1"
                  />
                  <line
                    x1={CHART_MARGIN.left}
                    y1={CHART_MARGIN.top + CHART_PLOT_HEIGHT}
                    x2={CHART_WIDTH - CHART_MARGIN.right}
                    y2={CHART_MARGIN.top + CHART_PLOT_HEIGHT}
                    stroke="#9aa9bc"
                    strokeWidth="1"
                  />

                  {chartBars.map((bar, index) => {
                    const style = statusStyle(bar.row.status);
                    return (
                      <rect
                        key={`health-bar-${index}`}
                        x={bar.x}
                        y={bar.y}
                        width={bar.width}
                        height={bar.height}
                        rx={Math.min(4, bar.width / 2)}
                        fill={statusPointColor(bar.row.status)}
                        opacity={0.72}
                        style={{ cursor: 'pointer' }}
                      >
                        <title>{`${formatDateTime(bar.row.windowStart)} ~ ${formatDateTime(bar.row.windowEnd)}\nHealth Index: ${formatPercent(bar.percent)}\n상태: ${style.label}\n이상 점수: ${formatAnomalyScore(bar.row.anomalyScore)}`}</title>
                      </rect>
                    );
                  })}

                  {chartLinePath ? (
                    <path
                      d={chartLinePath}
                      fill="none"
                      stroke="#0d47a1"
                      strokeWidth="2.4"
                      strokeLinejoin="round"
                      strokeLinecap="round"
                      opacity="0.95"
                    />
                  ) : null}

                  {chartBars.map((bar, index) => {
                    const style = statusStyle(bar.row.status);
                    const x = bar.x + bar.width / 2;
                    const y = bar.y;
                    return (
                      <circle
                        key={`health-line-point-${index}`}
                        cx={x}
                        cy={y}
                        r={3.7}
                        fill="#ffffff"
                        stroke={statusPointColor(bar.row.status)}
                        strokeWidth="2.2"
                        style={{ cursor: 'pointer' }}
                      >
                        <title>{`${formatDateTime(bar.row.windowStart)} ~ ${formatDateTime(bar.row.windowEnd)}\nHealth Index: ${formatPercent(bar.percent)}\n상태: ${style.label}\n이상 점수: ${formatAnomalyScore(bar.row.anomalyScore)}`}</title>
                      </circle>
                    );
                  })}

                  <text
                    x={CHART_MARGIN.left + CHART_PLOT_WIDTH / 2}
                    y={CHART_HEIGHT - 8}
                    textAnchor="middle"
                    fontSize="12"
                    fill="#667788"
                    fontWeight="600"
                  >
                    시간 (window_start)
                  </text>
                  <text
                    x={18}
                    y={CHART_MARGIN.top + CHART_PLOT_HEIGHT / 2}
                    textAnchor="middle"
                    fontSize="12"
                    fill="#667788"
                    fontWeight="600"
                    transform={`rotate(-90, 18, ${CHART_MARGIN.top + CHART_PLOT_HEIGHT / 2})`}
                  >
                    Health Index (%)
                  </text>
                </svg>
                <Stack direction="row" spacing={1.4} sx={{ mt: 0.5, px: 0.5, flexWrap: 'wrap', rowGap: 0.4 }}>
                  <Typography variant="caption" color="text.secondary">
                    막대: Health Index 값
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    파란 선: Health Index 추세
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    마우스를 올리면 시간/점수/상태/이상 점수를 확인할 수 있습니다.
                  </Typography>
                </Stack>
              </Box>
            ) : null}
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent sx={{ py: 1.5 }}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              현재 상태 요약
            </Typography>
            <Divider sx={{ my: 1 }} />

            <Stack spacing={1}>
              <Box>
                <Typography variant="body2" color="text.secondary">
                  최신 Health Index
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 800, color: '#1565c0', lineHeight: 1.2 }}>
                  {formatPercent(summary.latestHealthIndexPercent)}
                </Typography>
              </Box>

              <Box>
                {(() => {
                  const style = statusStyle(summary.latestStatus);
                  return (
                    <Box
                      component="span"
                      sx={{
                        px: 1.1,
                        py: 0.35,
                        borderRadius: 999,
                        fontSize: 12,
                        fontWeight: 700,
                        color: style.color,
                        backgroundColor: style.backgroundColor,
                        border: '1px solid rgba(0,0,0,0.06)',
                        display: 'inline-flex',
                      }}
                    >
                      {style.label}
                    </Box>
                  );
                })()}
              </Box>

              <Divider />

              <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 0.8 }}>
                <Typography variant="body2" color="text.secondary">
                  평균
                </Typography>
                <Typography variant="body2" sx={{ textAlign: 'right', fontWeight: 700 }}>
                  {formatPercent(summary.avgHealthIndexPercent)}
                </Typography>

                <Typography variant="body2" color="text.secondary">
                  최저
                </Typography>
                <Typography variant="body2" sx={{ textAlign: 'right', fontWeight: 700 }}>
                  {formatPercent(summary.minHealthIndexPercent)}
                </Typography>

                <Typography variant="body2" color="text.secondary">
                  최고
                </Typography>
                <Typography variant="body2" sx={{ textAlign: 'right', fontWeight: 700 }}>
                  {formatPercent(summary.maxHealthIndexPercent)}
                </Typography>

                <Typography variant="body2" color="text.secondary">
                  전체 window
                </Typography>
                <Typography variant="body2" sx={{ textAlign: 'right', fontWeight: 700 }}>
                  {formatCount(summary.totalCount)}
                </Typography>
              </Box>

              <Divider />

              <Stack direction="row" spacing={0.6} sx={{ flexWrap: 'wrap', rowGap: 0.6 }}>
                <Box
                  component="span"
                  sx={{
                    px: 0.9,
                    py: 0.35,
                    borderRadius: 999,
                    fontSize: 12,
                    fontWeight: 700,
                    color: '#1b5e20',
                    backgroundColor: '#e8f5e9',
                  }}
                >
                  NORMAL {formatCount(summary.normalCount)}
                </Box>
                <Box
                  component="span"
                  sx={{
                    px: 0.9,
                    py: 0.35,
                    borderRadius: 999,
                    fontSize: 12,
                    fontWeight: 700,
                    color: '#a84300',
                    backgroundColor: '#fff3e0',
                  }}
                >
                  WARNING {formatCount(summary.warningCount)}
                </Box>
                <Box
                  component="span"
                  sx={{
                    px: 0.9,
                    py: 0.35,
                    borderRadius: 999,
                    fontSize: 12,
                    fontWeight: 700,
                    color: '#b71c1c',
                    backgroundColor: '#ffebee',
                  }}
                >
                  CRITICAL {formatCount(summary.criticalCount)}
                </Box>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      </Box>

      <Card variant="outlined">
        <CardContent sx={{ py: 1.6 }}>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Health Index 상세 목록
          </Typography>
          <Divider sx={{ my: 1 }} />

          {loadingTrend ? (
            <Stack direction="row" spacing={1} alignItems="center" sx={{ py: 2 }}>
              <CircularProgress size={20} />
              <Typography variant="body2" color="text.secondary">
                상세 목록을 불러오는 중입니다.
              </Typography>
            </Stack>
          ) : null}

          {!loadingTrend && tableRows.length === 0 ? <Alert severity="info">{NO_TREND_MESSAGE}</Alert> : null}

          {tableRows.length > 0 ? (
            <TableContainer sx={{ border: '1px solid #d7e0ee', borderRadius: 1, maxHeight: 330 }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 800, width: 60 }}>No</TableCell>
                    <TableCell sx={{ fontWeight: 800, minWidth: 230 }}>시간(Window)</TableCell>
                    <TableCell sx={{ fontWeight: 800, width: 120 }}>Health Index</TableCell>
                    <TableCell sx={{ fontWeight: 800, width: 110 }}>이상 점수</TableCell>
                    <TableCell sx={{ fontWeight: 800, width: 105 }}>상태</TableCell>
                    <TableCell sx={{ fontWeight: 800, width: 92 }}>이상 여부</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {tableRows.map((row, index) => {
                    const style = statusStyle(row.status);
                    return (
                      <TableRow key={`${row.runId}-${row.windowStart ?? 'start'}-${row.windowEnd ?? 'end'}-${index}`} hover>
                        <TableCell>{index + 1}</TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                            {formatDateTime(row.windowStart)}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            ~ {formatDateTime(row.windowEnd)}
                          </Typography>
                        </TableCell>
                        <TableCell>{formatPercent(row.healthIndexPercent)}</TableCell>
                        <TableCell>{formatAnomalyScore(row.anomalyScore)}</TableCell>
                        <TableCell>
                          <Box
                            component="span"
                            sx={{
                              px: 0.9,
                              py: 0.3,
                              borderRadius: 999,
                              fontSize: 12,
                              fontWeight: 700,
                              color: style.color,
                              backgroundColor: style.backgroundColor,
                              border: '1px solid rgba(0,0,0,0.06)',
                            }}
                          >
                            {style.label}
                          </Box>
                        </TableCell>
                        <TableCell>
                          {row.isAnomaly == null ? '-' : row.isAnomaly ? '이상' : '정상'}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          ) : null}
        </CardContent>
      </Card>
    </Stack>
  );
}
