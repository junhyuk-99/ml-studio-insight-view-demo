import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';
import type { AnomalyResultPoint } from '../../types/modelTrain';
import { formatDateTime, formatNumber, statusPointColor } from './utils';

type AnomalyScoreTimelineChartProps = {
  rows: AnomalyResultPoint[];
  algoCode?: string | null;
  threshold?: number;
};

type PlotPoint = {
  x: number;
  y: number;
  score: number;
  isAnomaly: boolean;
  status: string | null;
  row: AnomalyResultPoint;
};

type TickPoint = {
  value: number;
  y: number;
};

const CHART_WIDTH = 1000;
const CHART_HEIGHT = 300;
const MARGIN = { top: 16, right: 16, bottom: 48, left: 56 };
const PLOT_WIDTH = CHART_WIDTH - MARGIN.left - MARGIN.right;
const PLOT_HEIGHT = CHART_HEIGHT - MARGIN.top - MARGIN.bottom;
const Y_TICK_COUNT = 5;
const IF_WARNING_THRESHOLD = 0.5;

function getChartRange(rows: AnomalyResultPoint[]) {
  const values = rows
    .map((row) => row.anomaly_score)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));

  const min = Math.min(...values, 0);
  const max = Math.max(...values, 1);
  const range = max - min > 0 ? max - min : 1;

  return { min, max, range };
}

function getPointColor(status: string | null, isAnomaly: boolean, score: number, threshold: number): string {
  const statusColor = statusPointColor(status, isAnomaly);
  if (statusColor !== '#4f6076') {
    return statusColor;
  }
  if (score >= threshold) {
    return '#d32f2f';
  }
  if (score >= IF_WARNING_THRESHOLD) {
    return '#ed6c02';
  }
  return '#2e7d32';
}

function getThresholdY(value: number, min: number, range: number): number {
  return MARGIN.top + (1 - (value - min) / range) * PLOT_HEIGHT;
}

function toPlotPoints(rows: AnomalyResultPoint[]): PlotPoint[] {
  const { min, range } = getChartRange(rows);

  if (rows.length === 0) {
    return [];
  }

  return rows.map((row, index) => {
    const score =
      typeof row.anomaly_score === 'number' && Number.isFinite(row.anomaly_score)
        ? row.anomaly_score
        : 0;

    const x =
      MARGIN.left + (rows.length <= 1 ? PLOT_WIDTH / 2 : (PLOT_WIDTH * index) / (rows.length - 1));
    const y = MARGIN.top + (1 - (score - min) / range) * PLOT_HEIGHT;

    return {
      x,
      y,
      score,
      isAnomaly: Boolean(row.is_anomaly),
      status: row.status,
      row,
    };
  });
}

function getYTicks(rows: AnomalyResultPoint[]): TickPoint[] {
  const { min, range } = getChartRange(rows);

  if (rows.length === 0) {
    return [];
  }

  const ticks: TickPoint[] = [];
  for (let i = 0; i <= Y_TICK_COUNT; i++) {
    const value = min + (range * i) / Y_TICK_COUNT;
    const y = MARGIN.top + (1 - (value - min) / range) * PLOT_HEIGHT;
    ticks.push({ value, y });
  }
  return ticks;
}

export function AnomalyScoreTimelineChart({
  rows,
  algoCode,
  threshold = 0.6,
}: AnomalyScoreTimelineChartProps) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  const plotPoints = useMemo(() => toPlotPoints(rows), [rows]);
  const yTicks = useMemo(() => getYTicks(rows), [rows]);
  const { min, range } = useMemo(() => getChartRange(rows), [rows]);
  const isIsolationForest = (algoCode ?? '').trim().toUpperCase() === 'ISOLATION_FOREST';

  const baselineY = MARGIN.top + PLOT_HEIGHT;
  const thresholdY = getThresholdY(threshold, min, range);
  const warningY = getThresholdY(IF_WARNING_THRESHOLD, min, range);

  const linePath =
    plotPoints.length > 0
      ? plotPoints
          .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
          .join(' ')
      : '';

  const areaPath =
    plotPoints.length > 1
      ? `${linePath} L ${plotPoints[plotPoints.length - 1].x} ${baselineY} L ${plotPoints[0].x} ${baselineY} Z`
      : '';

  const hoveredPoint = hoveredIndex != null ? plotPoints[hoveredIndex] : null;

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="subtitle1" fontWeight={700}>
          이상 점수 타임라인
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.4 }}>
          X축은 시간 구간, Y축은 이상 점수입니다. 임계값 선과 포인트 색상으로 정상, 경고, 이상 상태를 함께 확인할 수 있습니다.
        </Typography>
        <Divider sx={{ my: 1.2 }} />

        {rows.length === 0 && (
          <Alert severity="info">시각화할 이상 탐지 결과가 없습니다.</Alert>
        )}

        {rows.length > 0 && (
          <Stack spacing={1.2}>
            <Box
              sx={{
                position: 'relative',
                width: '100%',
                overflowX: 'auto',
                border: '1px solid #d6deea',
                borderRadius: 2,
                p: 1,
                backgroundColor: '#fff',
              }}
            >
              <svg
                viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
                width="100%"
                preserveAspectRatio="xMidYMid meet"
                role="img"
                aria-label="이상 점수 타임라인 차트"
              >
                {yTicks.map((tick, i) => (
                  <g key={`ytick-${i}`}>
                    <line
                      x1={MARGIN.left}
                      y1={tick.y}
                      x2={CHART_WIDTH - MARGIN.right}
                      y2={tick.y}
                      stroke="#e8ecf1"
                      strokeWidth="1"
                    />
                    <text
                      x={MARGIN.left - 8}
                      y={tick.y + 4}
                      textAnchor="end"
                      fontSize="11"
                      fill="#8896a8"
                    >
                      {tick.value.toFixed(2)}
                    </text>
                  </g>
                ))}

                <line
                  x1={MARGIN.left}
                  y1={MARGIN.top}
                  x2={MARGIN.left}
                  y2={baselineY}
                  stroke="#9aa9bc"
                  strokeWidth="1"
                />
                <line
                  x1={MARGIN.left}
                  y1={baselineY}
                  x2={CHART_WIDTH - MARGIN.right}
                  y2={baselineY}
                  stroke="#9aa9bc"
                  strokeWidth="1"
                />

                {isIsolationForest && (
                  <>
                    <line
                      x1={MARGIN.left}
                      y1={warningY}
                      x2={CHART_WIDTH - MARGIN.right}
                      y2={warningY}
                      stroke="#ed6c02"
                      strokeWidth="1.4"
                      strokeDasharray="5 4"
                    />
                    <text
                      x={CHART_WIDTH - MARGIN.right}
                      y={warningY - 4}
                      textAnchor="end"
                      fontSize="11"
                      fill="#ed6c02"
                      fontWeight="600"
                    >
                      주의 {IF_WARNING_THRESHOLD.toFixed(2)}
                    </text>

                    <line
                      x1={MARGIN.left}
                      y1={thresholdY}
                      x2={CHART_WIDTH - MARGIN.right}
                      y2={thresholdY}
                      stroke="#d32f2f"
                      strokeWidth="1.6"
                      strokeDasharray="6 4"
                    />
                    <text
                      x={CHART_WIDTH - MARGIN.right}
                      y={thresholdY - 4}
                      textAnchor="end"
                      fontSize="11"
                      fill="#d32f2f"
                      fontWeight="700"
                    >
                      이상 기준 {threshold.toFixed(2)}
                    </text>
                  </>
                )}

                {areaPath && <path d={areaPath} fill="rgba(22, 96, 207, 0.12)" />}
                {linePath && <path d={linePath} fill="none" stroke="#1660cf" strokeWidth="2.2" />}

                {plotPoints.map((point, index) => {
                  const fill = getPointColor(point.status, point.isAnomaly, point.score, threshold);
                  const isHovered = hoveredIndex === index;

                  return (
                    <g key={`point-${index}`}>
                      <circle
                        cx={point.x}
                        cy={point.y}
                        r={isHovered ? 6 : point.isAnomaly ? 4.6 : 3.4}
                        fill={fill}
                        stroke={isHovered ? '#0b3d91' : '#ffffff'}
                        strokeWidth={isHovered ? 2 : 1}
                        onMouseEnter={() => setHoveredIndex(index)}
                        onMouseLeave={() => setHoveredIndex((prev) => (prev === index ? null : prev))}
                      />
                    </g>
                  );
                })}

                <text
                  x={18}
                  y={MARGIN.top + PLOT_HEIGHT / 2}
                  textAnchor="middle"
                  fontSize="12"
                  fill="#667788"
                  fontWeight="600"
                  transform={`rotate(-90, 18, ${MARGIN.top + PLOT_HEIGHT / 2})`}
                >
                  이상 점수
                </text>

                <text
                  x={MARGIN.left + PLOT_WIDTH / 2}
                  y={CHART_HEIGHT - 8}
                  textAnchor="middle"
                  fontSize="12"
                  fill="#667788"
                  fontWeight="600"
                >
                  시간
                </text>
              </svg>

              {hoveredPoint && (
                <Box
                  sx={{
                    position: 'absolute',
                    top: 14,
                    right: 14,
                    minWidth: 220,
                    maxWidth: 280,
                    p: 1.2,
                    border: '1px solid',
                    borderColor: 'divider',
                    borderRadius: 1.5,
                    backgroundColor: 'rgba(255,255,255,0.96)',
                    boxShadow: 2,
                  }}
                >
                  <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.4 }}>
                    선택 구간 정보
                  </Typography>
                  <Typography variant="caption" display="block" color="text.secondary">
                    시작: {formatDateTime(hoveredPoint.row.window_start ?? null)}
                  </Typography>
                  <Typography variant="caption" display="block" color="text.secondary">
                    종료: {formatDateTime(hoveredPoint.row.window_end ?? null)}
                  </Typography>
                  <Typography variant="caption" display="block" color="text.secondary">
                    이상 점수: {formatNumber(hoveredPoint.score)}
                  </Typography>
                  <Typography variant="caption" display="block" color="text.secondary">
                    상태: {hoveredPoint.status ?? '-'}
                  </Typography>
                </Box>
              )}
            </Box>

            {isIsolationForest && (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip
                  size="small"
                  label="정상"
                  sx={{
                    backgroundColor: '#2e7d32',
                    color: '#fff',
                    fontWeight: 700,
                  }}
                />
                <Chip
                  size="small"
                  label={`경고 (${IF_WARNING_THRESHOLD.toFixed(2)} 이상)`}
                  sx={{
                    backgroundColor: '#ed6c02',
                    color: '#fff',
                    fontWeight: 700,
                  }}
                />
                <Chip
                  size="small"
                  label={`이상 (${threshold.toFixed(2)} 이상)`}
                  sx={{
                    backgroundColor: '#d32f2f',
                    color: '#fff',
                    fontWeight: 700,
                  }}
                />
              </Stack>
            )}

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip
                size="small"
                label={`시작: ${formatDateTime(rows[0]?.window_start ?? null)}`}
                variant="outlined"
                sx={{ maxWidth: 'none' }}
              />
              <Chip
                size="small"
                label={`종료: ${formatDateTime(rows[rows.length - 1]?.window_end ?? null)}`}
                variant="outlined"
                sx={{ maxWidth: 'none' }}
              />
              <Chip
                size="small"
                label={`최신 점수: ${formatNumber(rows[rows.length - 1]?.anomaly_score)}`}
                color="primary"
                variant="outlined"
                sx={{ maxWidth: 'none' }}
              />
            </Stack>
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}
