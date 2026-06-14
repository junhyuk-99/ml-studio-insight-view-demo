import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import RestartAltRoundedIcon from '@mui/icons-material/RestartAltRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { MouseEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { dataExplorationService } from '../services/dataExplorationService';
import type {
  DataExplorationDatasetOption,
  ProcessFlowCurrentState,
  ProcessFlowEquipmentOption,
  ProcessFlowEvent,
  ProcessFlowLatest,
  ProcessFlowOpstatMapping,
  ProcessFlowResponse,
  ProcessFlowStageSummary,
  ProcessFlowTemperaturePoint,
} from '../types/dataExploration';
import {
  filterDataExplorationDatasetOptions,
  persistDataExplorationDatasetKey,
  resolveDataExplorationDatasetKeyWithFallback,
  resolveDatasetDisplayName,
} from '../utils/dataExplorationDataset';
import {
  formatKstAxisLabel,
  formatKstDateTime,
  getCurrentKstDateTimeInput,
  shiftKstDateTimeInputHours,
  toUtcIsoFromKstDateTimeInput,
} from '../utils/kstDateTime';

const DEFAULT_LIMIT = 5000;
const TEMPERATURE_FIELDS = ['T1_PV', 'T2_PV', 'T1_SV', 'T2_SV'];
const AUTO_REFRESH_OPTIONS = [
  { label: 'Off', value: 0 },
  { label: '5초', value: 5 },
  { label: '10초', value: 10 },
  { label: '30초', value: 30 },
  { label: '60초', value: 60 },
];

const STATE_COLORS: Record<string, { bg: string; fill: string; border: string; text: string }> = {
  state0: { bg: '#eef2f7', fill: '#d9dee7', border: '#c7d0df', text: '#344256' },
  state1: { bg: '#fff1e7', fill: '#fed7aa', border: '#fdba74', text: '#9a3412' },
  state2: { bg: '#fff7d6', fill: '#fde68a', border: '#facc15', text: '#854d0e' },
  state3: { bg: '#eaf7ef', fill: '#bbf7d0', border: '#86efac', text: '#166534' },
  state4: { bg: '#f2e9ff', fill: '#d8b4fe', border: '#c084fc', text: '#6b21a8' },
  state5: { bg: '#ecfeff', fill: '#a5f3fc', border: '#67e8f9', text: '#155e75' },
  state6: { bg: '#eaf2ff', fill: '#bfdbfe', border: '#93c5fd', text: '#1d4ed8' },
  state7: { bg: '#eaf8ed', fill: '#bbf7d0', border: '#86efac', text: '#166534' },
  state8: { bg: '#f8eef3', fill: '#fbcfe8', border: '#f9a8d4', text: '#9d174d' },
};

const SERIES_DEFS = [
  { key: 't1Pv', label: 'T1_PV', color: '#dc2626', dashed: false },
  { key: 't2Pv', label: 'T2_PV', color: '#2563eb', dashed: false },
  { key: 't1Sv', label: 'T1_SV', color: '#ef4444', dashed: true },
  { key: 't2Sv', label: 'T2_SV', color: '#60a5fa', dashed: true },
] as const;

function createInitialRange(): { start: string; end: string } {
  const end = getCurrentKstDateTimeInput();
  return {
    start: shiftKstDateTimeInputHours(end, -8),
    end,
  };
}

function normalizeMccode(value: string | null | undefined): string {
  return value?.trim().toUpperCase() ?? '';
}

function formatNumber(value: number | null | undefined, digits = 1): string {
  if (value == null || Number.isNaN(value)) {
    return '-';
  }
  return value.toLocaleString('ko-KR', {
    maximumFractionDigits: digits,
  });
}

function formatTemperature(value: number | null | undefined): string {
  return value == null ? '-' : `${formatNumber(value, 1)} ℃`;
}

function formatDuration(totalSeconds: number | null | undefined): string {
  const safeSeconds = Math.max(0, Math.floor(totalSeconds ?? 0));
  const hours = Math.floor(safeSeconds / 3600);
  const minutes = Math.floor((safeSeconds % 3600) / 60);
  const seconds = safeSeconds % 60;
  return [hours, minutes, seconds].map((value) => String(value).padStart(2, '0')).join(':');
}

function stateColor(mapping: ProcessFlowOpstatMapping | null | undefined) {
  return STATE_COLORS[mapping?.colorKey ?? ''] ?? STATE_COLORS.state0;
}

function resolveStateMapping(
  mappings: ProcessFlowOpstatMapping[],
  code: number | null | undefined,
): ProcessFlowOpstatMapping | null {
  if (code == null) {
    return null;
  }
  return mappings.find((mapping) => mapping.code === code) ?? null;
}

function resolveEquipmentLabel(option: ProcessFlowEquipmentOption): string {
  const code = normalizeMccode(option.MCCODE);
  const name = option.MCNAME?.trim();
  if (code && name) {
    return `${code} / ${name}`;
  }
  return code || name || '-';
}

function resolveDatasetEquipment(
  datasetKey: string,
  equipmentOptions: ProcessFlowEquipmentOption[],
): string {
  const normalizedDatasetKey = datasetKey.trim();
  if (!normalizedDatasetKey) {
    return '';
  }
  const matched = equipmentOptions.find((option) => option.dataset_key === normalizedDatasetKey);
  return normalizeMccode(matched?.MCCODE);
}

function buildKpiItems(data: ProcessFlowResponse | null) {
  const latest = data?.latest ?? null;
  const current = data?.currentState ?? null;
  const avgPv =
    latest?.t1Pv != null && latest?.t2Pv != null
      ? (latest.t1Pv + latest.t2Pv) / 2
      : latest?.t1Pv ?? latest?.t2Pv ?? null;
  const avgSv =
    latest?.t1Sv != null && latest?.t2Sv != null
      ? (latest.t1Sv + latest.t2Sv) / 2
      : latest?.t1Sv ?? latest?.t2Sv ?? null;
  const alarmText = latest?.opalarm == null || latest.opalarm === '0' ? '정상' : latest.opalarm;

  return [
    { label: '현재 설비', value: data ? data.equipmentName || data.mccode : '-', sub: data?.mccode ?? '-' },
    { label: '현재 공정 상태', value: current?.label ?? latest?.opstatLabel ?? '-', sub: data?.opstatGroup ?? '-' },
    { label: '현재 온도 평균', value: formatTemperature(avgPv), sub: `T1 ${formatTemperature(latest?.t1Pv)} / T2 ${formatTemperature(latest?.t2Pv)}` },
    { label: '목표/설정 온도', value: formatTemperature(avgSv), sub: `T1 ${formatTemperature(latest?.t1Sv)} / T2 ${formatTemperature(latest?.t2Sv)}` },
    { label: '현재 단계 경과 시간', value: formatDuration(current?.elapsedSeconds), sub: `row ${current?.rowCount?.toLocaleString('ko-KR') ?? 0}건` },
    { label: '알람 상태', value: alarmText, sub: `OPALARM ${latest?.opalarm ?? '-'}` },
  ];
}

function KpiGrid({ data }: { data: ProcessFlowResponse | null }) {
  const items = buildKpiItems(data);
  return (
    <Box
      sx={{
        display: 'grid',
        gap: 1.2,
        gridTemplateColumns: {
          xs: '1fr',
          sm: 'repeat(2, minmax(0, 1fr))',
          lg: 'repeat(6, minmax(0, 1fr))',
        },
      }}
    >
      {items.map((item) => (
        <Card key={item.label} variant="outlined" sx={{ borderRadius: 2, borderColor: '#dce4ef' }}>
          <CardContent sx={{ py: 1.6 }}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
              {item.label}
            </Typography>
            <Typography variant="h6" sx={{ mt: 0.6, fontWeight: 900, lineHeight: 1.2 }}>
              {item.value}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.4 }}>
              {item.sub}
            </Typography>
          </CardContent>
        </Card>
      ))}
    </Box>
  );
}

function ProcessFlowStrip({ data }: { data: ProcessFlowResponse | null }) {
  const currentCode = data?.currentState?.code ?? data?.latest?.opstat ?? null;
  const mappings = data?.opstatMapping ?? [];

  if (mappings.length === 0) {
    return <Alert severity="info">선택 설비의 공정 상태 매핑이 없습니다.</Alert>;
  }

  return (
    <Box sx={{ overflowX: 'auto', pb: 0.2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 880 }}>
        {mappings.map((mapping, index) => {
          const active = currentCode === mapping.code;
          const colors = stateColor(mapping);
          return (
            <Box key={mapping.code} sx={{ display: 'flex', alignItems: 'center', flex: 1, minWidth: 110 }}>
              <Box
                sx={{
                  width: '100%',
                  px: 1.4,
                  py: 1.1,
                  borderRadius: 1.5,
                  border: '1px solid',
                  borderColor: active ? colors.text : colors.border,
                  bgcolor: active ? colors.fill : colors.bg,
                  color: colors.text,
                  textAlign: 'center',
                  boxShadow: active ? '0 4px 14px rgba(15, 23, 42, 0.16)' : 'none',
                }}
              >
                <Typography variant="caption" sx={{ display: 'block', fontWeight: 800 }}>
                  {mapping.code}
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 900, whiteSpace: 'nowrap' }}>
                  {mapping.label}
                </Typography>
              </Box>
              {index < mappings.length - 1 && (
                <Typography sx={{ mx: 0.7, color: '#64748b', fontWeight: 900 }}>→</Typography>
              )}
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

type ChartPoint = {
  x: number;
  y: number;
  row: ProcessFlowTemperaturePoint;
};

function buildLinePaths(points: Array<ChartPoint | null>): string[] {
  const paths: string[] = [];
  let current: string[] = [];

  for (const point of points) {
    if (point == null) {
      if (current.length > 0) {
        paths.push(current.join(' '));
        current = [];
      }
      continue;
    }
    current.push(`${current.length === 0 ? 'M' : 'L'} ${point.x} ${point.y}`);
  }
  if (current.length > 0) {
    paths.push(current.join(' '));
  }
  return paths;
}

function TemperatureTrendChart({
  data,
}: {
  data: ProcessFlowResponse | null;
}) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  const series = data?.temperatureSeries ?? [];
  const segments = data?.processSegments ?? [];
  const mappings = data?.opstatMapping ?? [];

  const values = series.flatMap((point) =>
    SERIES_DEFS.map((definition) => point[definition.key]).filter(
      (value): value is number => value != null && Number.isFinite(value),
    ),
  );

  if (!data || series.length === 0) {
    return <Alert severity="info">조회된 온도 데이터가 없습니다.</Alert>;
  }
  if (values.length === 0) {
    return <Alert severity="info">표시 가능한 PV/SV 온도 값이 없습니다.</Alert>;
  }

  const width = 1120;
  const height = 430;
  const margin = { top: 48, right: 34, bottom: 58, left: 74 };
  const plotWidth = width - margin.left - margin.right;
  const plotHeight = height - margin.top - margin.bottom;

  const firstTime = new Date(series[0].prdtime).getTime();
  const lastTime = new Date(series[series.length - 1].prdtime).getTime();
  const startMs = Number.isFinite(firstTime) ? firstTime : Date.now();
  const endMs = Number.isFinite(lastTime) && lastTime > startMs ? lastTime : startMs + 1;
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const valuePadding = Math.max((maxValue - minValue) * 0.08, 5);
  const yMin = minValue - valuePadding;
  const yMax = maxValue + valuePadding;
  const valueRange = yMax - yMin || 1;

  const xForTime = (isoText: string) => {
    const time = new Date(isoText).getTime();
    const clamped = Math.min(endMs, Math.max(startMs, Number.isFinite(time) ? time : startMs));
    return margin.left + ((clamped - startMs) / (endMs - startMs)) * plotWidth;
  };
  const yForValue = (value: number) => margin.top + (1 - (value - yMin) / valueRange) * plotHeight;

  const yTicks = Array.from({ length: 6 }).map((_, index) => {
    const ratio = index / 5;
    const value = yMin + valueRange * ratio;
    return {
      value,
      y: margin.top + (1 - ratio) * plotHeight,
    };
  });

  const xTickIndexes = Array.from(
    new Set([0, Math.floor(series.length / 3), Math.floor((series.length * 2) / 3), series.length - 1]),
  ).filter((index) => index >= 0 && index < series.length);

  const hoveredPoint =
    hoveredIndex == null ? null : series[Math.min(Math.max(hoveredIndex, 0), series.length - 1)];

  const handleMouseMove = (event: MouseEvent<SVGRectElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width <= 0 || series.length === 0) {
      return;
    }
    const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
    setHoveredIndex(Math.round(ratio * (series.length - 1)));
  };

  const latestPoint = series[series.length - 1];
  const latestX = xForTime(latestPoint.prdtime);

  return (
    <Box sx={{ position: 'relative', overflowX: 'auto' }}>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        width="100%"
        preserveAspectRatio="xMidYMid meet"
        role="img"
        aria-label="실시간 온도 추이 차트"
      >
        {segments.map((segment, index) => {
          const mapping = resolveStateMapping(mappings, segment.opstat);
          const colors = stateColor(mapping);
          const x1 = xForTime(segment.start);
          const x2 = xForTime(segment.end);
          const bandWidth = Math.max(1, x2 - x1);
          return (
            <g key={`${segment.start}-${index}`}>
              <rect
                x={x1}
                y={margin.top}
                width={bandWidth}
                height={plotHeight}
                fill={colors.fill}
                opacity={0.25}
              />
              {bandWidth > 52 && (
                <text x={x1 + bandWidth / 2} y={margin.top - 16} textAnchor="middle" fontSize={11} fill={colors.text} fontWeight={800}>
                  {segment.opstat ?? '-'} {segment.label}
                </text>
              )}
            </g>
          );
        })}

        {yTicks.map((tick, index) => (
          <g key={`y-${index}`}>
            <line x1={margin.left} y1={tick.y} x2={width - margin.right} y2={tick.y} stroke="#e2e8f0" />
            <text x={margin.left - 10} y={tick.y + 4} textAnchor="end" fontSize={11} fill="#64748b">
              {formatNumber(tick.value, 0)}
            </text>
          </g>
        ))}

        <line x1={margin.left} y1={margin.top} x2={margin.left} y2={height - margin.bottom} stroke="#94a3b8" />
        <line x1={margin.left} y1={height - margin.bottom} x2={width - margin.right} y2={height - margin.bottom} stroke="#94a3b8" />

        {SERIES_DEFS.map((definition) => {
          const chartPoints = series
            .map((row) => {
              const value = row[definition.key];
              if (value == null || !Number.isFinite(value)) {
                return null;
              }
              return {
                x: xForTime(row.prdtime),
                y: yForValue(value),
                row,
              };
            });
          return buildLinePaths(chartPoints).map((path, pathIndex) => (
            <path
              key={`${definition.key}-${pathIndex}`}
              d={path}
              fill="none"
              stroke={definition.color}
              strokeWidth={2.2}
              strokeDasharray={definition.dashed ? '7 5' : undefined}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          ));
        })}

        <line
          x1={latestX}
          y1={margin.top}
          x2={latestX}
          y2={height - margin.bottom}
          stroke="#7c3aed"
          strokeDasharray="5 5"
          strokeWidth={1.4}
        />
        <circle cx={latestX} cy={margin.top + 10} r={4} fill="#7c3aed" />

        {xTickIndexes.map((index) => {
          const point = series[index];
          const x = xForTime(point.prdtime);
          return (
            <g key={`x-${point.prdtime}-${index}`}>
              <line x1={x} y1={height - margin.bottom} x2={x} y2={height - margin.bottom + 5} stroke="#94a3b8" />
              <text x={x} y={height - margin.bottom + 20} textAnchor="middle" fontSize={11} fill="#64748b">
                {formatKstAxisLabel(point.prdtime)}
              </text>
            </g>
          );
        })}

        <text x={margin.left + plotWidth / 2} y={height - 14} textAnchor="middle" fontSize={12} fontWeight={800} fill="#334155">
          시간(KST)
        </text>
        <text x={margin.left - 14} y={margin.top - 10} textAnchor="end" fontSize={12} fontWeight={800} fill="#334155">
          온도(℃)
        </text>

        <rect
          x={margin.left}
          y={margin.top}
          width={plotWidth}
          height={plotHeight}
          fill="transparent"
          onMouseMove={handleMouseMove}
          onMouseLeave={() => setHoveredIndex(null)}
        />
      </svg>

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mt: 1 }}>
        {SERIES_DEFS.map((definition) => (
          <Chip
            key={definition.key}
            size="small"
            variant="outlined"
            label={definition.label}
            sx={{ borderColor: definition.color, color: definition.color, fontWeight: 800 }}
          />
        ))}
      </Box>

      {hoveredPoint && (
        <Box
          sx={{
            position: 'absolute',
            top: 12,
            right: 12,
            minWidth: 250,
            p: 1.2,
            border: '1px solid',
            borderColor: '#d6deeb',
            borderRadius: 1.5,
            bgcolor: 'rgba(255, 255, 255, 0.97)',
            boxShadow: '0 8px 22px rgba(15, 23, 42, 0.14)',
          }}
        >
          <Typography variant="caption" display="block" sx={{ fontWeight: 900 }}>
            {formatKstDateTime(hoveredPoint.prdtime)}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            OPSTAT {hoveredPoint.opstat ?? '-'} / {hoveredPoint.opstatLabel ?? '-'}
          </Typography>
          {SERIES_DEFS.map((definition) => (
            <Typography key={definition.key} variant="caption" display="block" color="text.secondary">
              {definition.label}: {formatTemperature(hoveredPoint[definition.key])}
            </Typography>
          ))}
          <Typography variant="caption" display="block" color="text.secondary">
            SEGMENT {hoveredPoint.segmentNo ?? '-'} / {hoveredPoint.segmentTotal ?? '-'}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            WORKORDER {hoveredPoint.workorder ?? '-'}
          </Typography>
        </Box>
      )}
    </Box>
  );
}

function DetailPanel({
  data,
}: {
  data: ProcessFlowResponse | null;
}) {
  const latest: ProcessFlowLatest | null = data?.latest ?? null;
  const current: ProcessFlowCurrentState | null = data?.currentState ?? null;
  const rows = [
    ['현재 단계', current ? `${current.label ?? '-'} (${current.code ?? '-'})` : '-'],
    ['공정 상태 그룹', data?.opstatGroup ?? '-'],
    ['시작 시간', formatKstDateTime(current?.startedAt)],
    ['경과 시간', formatDuration(current?.elapsedSeconds)],
    ['최신 PRDTIME', formatKstDateTime(latest?.prdtime)],
    ['MCCODE / Equipment', data ? `${data.mccode} / ${data.equipmentName ?? '-'}` : '-'],
    ['WORKORDER', latest?.workorder ?? '-'],
    ['PAT_PGM', latest?.patPgm ?? '-'],
    ['SEGMENT', `${latest?.segmentNo ?? '-'} / ${latest?.segmentTotal ?? '-'}`],
    ['OPALARM', latest?.opalarm ?? '-'],
    ['T1/T2 PV', `${formatTemperature(latest?.t1Pv)} / ${formatTemperature(latest?.t2Pv)}`],
    ['T1/T2 SV', `${formatTemperature(latest?.t1Sv)} / ${formatTemperature(latest?.t2Sv)}`],
  ];

  return (
    <Stack spacing={0}>
      {rows.map(([label, value]) => (
        <Box
          key={label}
          sx={{
            display: 'grid',
            gridTemplateColumns: '132px minmax(0, 1fr)',
            gap: 1,
            py: 0.85,
            borderBottom: '1px solid',
            borderColor: '#edf1f7',
          }}
        >
          <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 700 }}>
            {label}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 800, wordBreak: 'break-word' }}>
            {value}
          </Typography>
        </Box>
      ))}
    </Stack>
  );
}

function StageSummaryBars({ rows }: { rows: ProcessFlowStageSummary[] }) {
  if (rows.length === 0) {
    return <Alert severity="info">공정 단계 요약 데이터가 없습니다.</Alert>;
  }

  const width = 900;
  const height = 260;
  const margin = { top: 20, right: 28, bottom: 62, left: 62 };
  const plotWidth = width - margin.left - margin.right;
  const plotHeight = height - margin.top - margin.bottom;
  const maxAvg = Math.max(
    1,
    ...rows.flatMap((row) => [row.avgT1Pv, row.avgT2Pv]).filter((value): value is number => value != null),
  );
  const slotWidth = plotWidth / Math.max(rows.length, 1);
  const barWidth = Math.min(24, Math.max(8, slotWidth / 4));

  return (
    <Box sx={{ overflowX: 'auto' }}>
      <svg viewBox={`0 0 ${width} ${height}`} width="100%" preserveAspectRatio="xMidYMid meet" role="img" aria-label="공정 단계별 평균 온도">
        {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
          const y = margin.top + (1 - ratio) * plotHeight;
          return (
            <g key={ratio}>
              <line x1={margin.left} y1={y} x2={width - margin.right} y2={y} stroke="#e2e8f0" />
              <text x={margin.left - 10} y={y + 4} textAnchor="end" fontSize={11} fill="#64748b">
                {formatNumber(maxAvg * ratio, 0)}
              </text>
            </g>
          );
        })}
        <line x1={margin.left} y1={margin.top} x2={margin.left} y2={height - margin.bottom} stroke="#94a3b8" />
        <line x1={margin.left} y1={height - margin.bottom} x2={width - margin.right} y2={height - margin.bottom} stroke="#94a3b8" />

        {rows.map((row, index) => {
          const centerX = margin.left + slotWidth * index + slotWidth / 2;
          const t1Height = ((row.avgT1Pv ?? 0) / maxAvg) * plotHeight;
          const t2Height = ((row.avgT2Pv ?? 0) / maxAvg) * plotHeight;
          return (
            <g key={`${row.start}-${index}`}>
              <rect x={centerX - barWidth - 2} y={margin.top + plotHeight - t1Height} width={barWidth} height={t1Height} rx={4} fill="#2563eb" />
              <rect x={centerX + 2} y={margin.top + plotHeight - t2Height} width={barWidth} height={t2Height} rx={4} fill="#60a5fa" />
              <text x={centerX} y={height - margin.bottom + 18} textAnchor="middle" fontSize={10} fill="#334155" fontWeight={800}>
                {row.opstat ?? '-'}
              </text>
              <text x={centerX} y={height - margin.bottom + 34} textAnchor="middle" fontSize={10} fill="#64748b">
                {formatDuration(row.durationSeconds)}
              </text>
            </g>
          );
        })}
        <text x={margin.left - 12} y={margin.top - 8} textAnchor="end" fontSize={12} fill="#334155" fontWeight={800}>
          평균 ℃
        </text>
      </svg>
    </Box>
  );
}

function StageSummaryTable({ rows }: { rows: ProcessFlowStageSummary[] }) {
  if (rows.length === 0) {
    return null;
  }
  return (
    <Box sx={{ mt: 1.2, overflowX: 'auto' }}>
      <Box sx={{ minWidth: 860 }}>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: '70px 120px 130px 130px 90px repeat(4, 86px)',
            gap: 1,
            px: 1,
            py: 0.8,
            bgcolor: '#f1f5f9',
            borderRadius: 1,
          }}
        >
          {['OPSTAT', '상태', '시작', '종료', '체류', 'T1 PV', 'T2 PV', 'T1 SV', 'T2 SV'].map((header) => (
            <Typography key={header} variant="caption" sx={{ fontWeight: 900, color: '#475569' }}>
              {header}
            </Typography>
          ))}
        </Box>
        {rows.map((row, index) => (
          <Box
            key={`${row.start}-${index}`}
            sx={{
              display: 'grid',
              gridTemplateColumns: '70px 120px 130px 130px 90px repeat(4, 86px)',
              gap: 1,
              px: 1,
              py: 0.9,
              borderBottom: '1px solid #edf1f7',
            }}
          >
            <Typography variant="caption" sx={{ fontWeight: 800 }}>{row.opstat ?? '-'}</Typography>
            <Typography variant="caption" sx={{ fontWeight: 800 }}>{row.label ?? '-'}</Typography>
            <Typography variant="caption">{formatKstAxisLabel(row.start)}</Typography>
            <Typography variant="caption">{formatKstAxisLabel(row.end)}</Typography>
            <Typography variant="caption">{formatDuration(row.durationSeconds)}</Typography>
            <Typography variant="caption">{formatNumber(row.avgT1Pv, 1)}</Typography>
            <Typography variant="caption">{formatNumber(row.avgT2Pv, 1)}</Typography>
            <Typography variant="caption">{formatNumber(row.avgT1Sv, 1)}</Typography>
            <Typography variant="caption">{formatNumber(row.avgT2Sv, 1)}</Typography>
          </Box>
        ))}
      </Box>
    </Box>
  );
}

function EventTimeline({ events }: { events: ProcessFlowEvent[] }) {
  if (events.length === 0) {
    return <Alert severity="info">상태 변경 이벤트가 없습니다.</Alert>;
  }

  return (
    <Stack spacing={1}>
      {events.slice(-12).reverse().map((event, index) => (
        <Box key={`${event.time}-${index}`} sx={{ display: 'grid', gridTemplateColumns: '86px minmax(0, 1fr)', gap: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800 }}>
            {formatKstAxisLabel(event.time)}
          </Typography>
          <Box sx={{ pb: 1, borderBottom: '1px solid #edf1f7' }}>
            <Chip size="small" label={event.type} sx={{ mb: 0.5, height: 22, fontWeight: 800 }} />
            <Typography variant="body2" sx={{ fontWeight: 800 }}>
              {event.message ?? `${event.fromLabel ?? '-'} -> ${event.toLabel ?? '-'}`}
            </Typography>
          </Box>
        </Box>
      ))}
    </Stack>
  );
}

export function DataExplorationProcessFlowPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [initialRange] = useState(createInitialRange);
  const [startDateTime, setStartDateTime] = useState(initialRange.start);
  const [endDateTime, setEndDateTime] = useState(initialRange.end);
  const [datasetOptions, setDatasetOptions] = useState<DataExplorationDatasetOption[]>([]);
  const [equipmentOptions, setEquipmentOptions] = useState<ProcessFlowEquipmentOption[]>([]);
  const [selectedDatasetKey, setSelectedDatasetKey] = useState('');
  const [selectedMccode, setSelectedMccode] = useState('');
  const [selectedOpstats, setSelectedOpstats] = useState<number[]>([]);
  const [autoRefreshSeconds, setAutoRefreshSeconds] = useState(0);
  const [processFlowData, setProcessFlowData] = useState<ProcessFlowResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [pageInfo, setPageInfo] = useState<string | null>(null);
  const [lastRefreshedAt, setLastRefreshedAt] = useState<string | null>(null);
  const requestInFlightRef = useRef(false);
  const initialLoadStartedRef = useRef(false);

  const queryDatasetKey = useMemo(
    () => searchParams.get('datasetKey')?.trim() || searchParams.get('dataset_key')?.trim() || '',
    [searchParams],
  );

  const opstatOptions = processFlowData?.opstatMapping ?? [];
  const selectedOpstatSet = useMemo(() => new Set(selectedOpstats), [selectedOpstats]);
  const selectedEquipment = useMemo(
    () => equipmentOptions.find((option) => normalizeMccode(option.MCCODE) === selectedMccode) ?? null,
    [equipmentOptions, selectedMccode],
  );

  const syncDatasetKeyQuery = useCallback(
    (datasetKey: string) => {
      const nextParams = new URLSearchParams(searchParams);
      if (datasetKey.trim()) {
        nextParams.set('datasetKey', datasetKey.trim());
      } else {
        nextParams.delete('datasetKey');
      }
      nextParams.delete('dataset_key');
      setSearchParams(nextParams, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const executeQuery = useCallback(
    async (options?: {
      datasetKey?: string;
      mccode?: string;
      startText?: string;
      endText?: string;
      opstats?: number[];
      auto?: boolean;
    }) => {
      if (requestInFlightRef.current) {
        if (!options?.auto) {
          setPageInfo('이전 조회가 진행 중입니다.');
        }
        return;
      }

      const datasetKey = options?.datasetKey ?? selectedDatasetKey;
      const mccode = normalizeMccode(options?.mccode ?? selectedMccode);
      const startText = options?.startText ?? startDateTime;
      const endText = options?.endText ?? endDateTime;
      const opstats = options?.opstats ?? selectedOpstats;

      if (!datasetKey.trim()) {
        setPageError(null);
        setPageInfo('데이터셋을 선택해 주세요.');
        return;
      }
      if (!mccode) {
        setPageError(null);
        setPageInfo('설비를 선택해 주세요.');
        return;
      }

      const startIso = toUtcIsoFromKstDateTimeInput(startText);
      const endIso = toUtcIsoFromKstDateTimeInput(endText);
      if (!startIso || !endIso) {
        setPageError('조회 기간 형식이 올바르지 않습니다.');
        setPageInfo(null);
        return;
      }
      if (startIso > endIso) {
        setPageError('시작 시간이 종료 시간보다 클 수 없습니다.');
        setPageInfo(null);
        return;
      }

      requestInFlightRef.current = true;
      setLoading(true);
      setPageError(null);
      if (!options?.auto) {
        setPageInfo(null);
      }

      try {
        const response = await dataExplorationService.getProcessFlow({
          datasetKey,
          mccode,
          start: startIso,
          end: endIso,
          opstats,
          fields: TEMPERATURE_FIELDS,
          limit: DEFAULT_LIMIT,
          autoRefresh: Boolean(options?.auto),
        });

        setProcessFlowData(response);
        setSelectedMccode(response.mccode);
        setLastRefreshedAt(new Date().toISOString());
        if (response.temperatureSeries.length === 0) {
          setPageInfo('조회된 데이터가 없습니다.');
        } else {
          setPageInfo(null);
        }
      } catch (error: unknown) {
        setProcessFlowData(null);
        setPageError(error instanceof Error ? error.message : '공정 흐름 데이터를 조회하지 못했습니다.');
      } finally {
        requestInFlightRef.current = false;
        setLoading(false);
      }
    },
    [endDateTime, selectedDatasetKey, selectedMccode, selectedOpstats, startDateTime],
  );

  const loadInitialData = useCallback(async () => {
    setInitialLoading(true);
    setPageError(null);

    try {
      const [datasetsRaw, equipmentsRaw] = await Promise.all([
        dataExplorationService.getDatasets(),
        dataExplorationService.getEquipmentOptions(),
      ]);
      const datasets = filterDataExplorationDatasetOptions(datasetsRaw);
      const equipments = equipmentsRaw
        .filter((equipment) => normalizeMccode(equipment.MCCODE))
        .sort((left, right) => normalizeMccode(left.MCCODE).localeCompare(normalizeMccode(right.MCCODE)));

      setDatasetOptions(datasets);
      setEquipmentOptions(equipments);

      const resolved = resolveDataExplorationDatasetKeyWithFallback({
        datasetOptions: datasets,
        queryDatasetKey,
        preferredDatasetKey: selectedDatasetKey,
      });
      const datasetKey = resolved.datasetKey;
      const equipmentFromDataset = resolveDatasetEquipment(datasetKey, equipments);
      const mccode = equipmentFromDataset || normalizeMccode(equipments[0]?.MCCODE);

      setSelectedDatasetKey(datasetKey);
      setSelectedMccode(mccode);
      if (datasetKey) {
        persistDataExplorationDatasetKey(datasetKey);
        syncDatasetKeyQuery(datasetKey);
      }
      if (resolved.warning) {
        setPageInfo(resolved.warning);
      }

      if (datasetKey && mccode) {
        await executeQuery({
          datasetKey,
          mccode,
          startText: initialRange.start,
          endText: initialRange.end,
          opstats: [],
        });
      }
    } catch (error: unknown) {
      setPageError(error instanceof Error ? error.message : '초기 조회 조건을 불러오지 못했습니다.');
    } finally {
      setInitialLoading(false);
    }
  }, [executeQuery, initialRange.end, initialRange.start, queryDatasetKey, selectedDatasetKey, syncDatasetKeyQuery]);

  useEffect(() => {
    if (initialLoadStartedRef.current) {
      return;
    }
    initialLoadStartedRef.current = true;
    void loadInitialData();
  }, [loadInitialData]);

  useEffect(() => {
    if (autoRefreshSeconds <= 0 || !selectedDatasetKey || !selectedMccode) {
      return undefined;
    }
    const intervalId = window.setInterval(() => {
      if (requestInFlightRef.current) {
        return;
      }
      const nextEnd = getCurrentKstDateTimeInput();
      setEndDateTime(nextEnd);
      void executeQuery({ endText: nextEnd, auto: true });
    }, autoRefreshSeconds * 1000);

    return () => window.clearInterval(intervalId);
  }, [autoRefreshSeconds, executeQuery, selectedDatasetKey, selectedMccode]);

  const handleDatasetChange = (nextDatasetKey: string) => {
    setSelectedDatasetKey(nextDatasetKey);
    persistDataExplorationDatasetKey(nextDatasetKey);
    syncDatasetKeyQuery(nextDatasetKey);
    const nextMccode = resolveDatasetEquipment(nextDatasetKey, equipmentOptions) || selectedMccode;
    setSelectedMccode(nextMccode);
    setSelectedOpstats([]);
    void executeQuery({ datasetKey: nextDatasetKey, mccode: nextMccode, opstats: [] });
  };

  const handleEquipmentChange = (nextMccode: string) => {
    const normalized = normalizeMccode(nextMccode);
    setSelectedMccode(normalized);
    setSelectedOpstats([]);
    void executeQuery({ mccode: normalized, opstats: [] });
  };

  const handleReset = () => {
    const nextRange = createInitialRange();
    const equipmentFromDataset = resolveDatasetEquipment(selectedDatasetKey, equipmentOptions);
    const nextMccode = equipmentFromDataset || normalizeMccode(equipmentOptions[0]?.MCCODE);
    setStartDateTime(nextRange.start);
    setEndDateTime(nextRange.end);
    setSelectedMccode(nextMccode);
    setSelectedOpstats([]);
    setAutoRefreshSeconds(0);
    void executeQuery({
      mccode: nextMccode,
      startText: nextRange.start,
      endText: nextRange.end,
      opstats: [],
    });
  };

  const handleOpstatChange = (value: unknown) => {
    const rawValues = Array.isArray(value) ? value : String(value).split(',');
    const nextValues = rawValues
      .map((item) => Number(item))
      .filter((item) => Number.isFinite(item));
    setSelectedOpstats(nextValues);
  };

  const selectedDatasetLabel = useMemo(() => {
    const option = datasetOptions.find((dataset) => dataset.datasetKey === selectedDatasetKey);
    return option ? resolveDatasetDisplayName(option) : selectedDatasetKey || '-';
  }, [datasetOptions, selectedDatasetKey]);

  const currentMapping = resolveStateMapping(opstatOptions, processFlowData?.latest?.opstat ?? null);
  const currentColors = stateColor(currentMapping);

  return (
    <Stack spacing={2.2}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>
          공정 흐름 분석
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.7 }}>
          원시 데이터를 기반으로 공정 상태별 흐름과 실시간 온도 변화를 모니터링합니다.
        </Typography>
      </Box>

      {pageError && <Alert severity="error">{pageError}</Alert>}
      {pageInfo && !pageError && <Alert severity="info">{pageInfo}</Alert>}
      {processFlowData?.warnings?.map((warning) => (
        <Alert key={warning} severity="warning">
          {warning}
        </Alert>
      ))}

      <Card variant="outlined" sx={{ borderRadius: 2, borderColor: '#dce4ef' }}>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              조회 조건
            </Typography>
            <Typography variant="body2" color="text.secondary">
              마지막 새로고침: {formatKstDateTime(lastRefreshedAt)}
            </Typography>
          </Box>
          <Divider sx={{ my: 1.2 }} />

          <Box
            sx={{
              display: 'grid',
              gap: 1.2,
              gridTemplateColumns: {
                xs: '1fr',
                md: 'repeat(2, minmax(0, 1fr))',
                xl: '1.4fr 1.2fr 1fr 1fr 1.3fr 0.8fr auto auto',
              },
              alignItems: 'start',
            }}
          >
            <TextField
              select
              label="데이터셋"
              value={selectedDatasetKey}
              onChange={(event) => handleDatasetChange(event.target.value)}
              disabled={initialLoading || loading || datasetOptions.length === 0}
              fullWidth
            >
              {datasetOptions.map((option) => (
                <MenuItem key={option.datasetKey} value={option.datasetKey}>
                  {resolveDatasetDisplayName(option)}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              label="설비"
              value={selectedMccode}
              onChange={(event) => handleEquipmentChange(event.target.value)}
              disabled={initialLoading || loading || equipmentOptions.length === 0}
              fullWidth
            >
              {equipmentOptions.map((option) => (
                <MenuItem key={normalizeMccode(option.MCCODE)} value={normalizeMccode(option.MCCODE)}>
                  {resolveEquipmentLabel(option)}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              label="시작 시간"
              type="datetime-local"
              value={startDateTime}
              onChange={(event) => setStartDateTime(event.target.value)}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />

            <TextField
              label="종료 시간"
              type="datetime-local"
              value={endDateTime}
              onChange={(event) => setEndDateTime(event.target.value)}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />

            <TextField
              select
              label="공정 상태"
              value={selectedOpstats.map(String)}
              onChange={(event) => handleOpstatChange(event.target.value)}
              SelectProps={{
                multiple: true,
                renderValue: (selected) => {
                  const selectedValues = Array.isArray(selected) ? selected : [];
                  if (selectedValues.length === 0) {
                    return '전체';
                  }
                  return selectedValues
                    .map((value) => opstatOptions.find((option) => String(option.code) === String(value))?.label ?? value)
                    .join(', ');
                },
              }}
              fullWidth
            >
              {opstatOptions.map((option) => (
                <MenuItem key={option.code} value={String(option.code)}>
                  <Checkbox checked={selectedOpstatSet.has(option.code)} size="small" />
                  <Typography variant="body2">
                    {option.code} / {option.label}
                  </Typography>
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              label="자동 새로고침"
              value={String(autoRefreshSeconds)}
              onChange={(event) => setAutoRefreshSeconds(Number(event.target.value))}
              fullWidth
            >
              {AUTO_REFRESH_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={String(option.value)}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>

            <Button
              variant="contained"
              startIcon={loading ? <CircularProgress size={16} color="inherit" /> : <SearchRoundedIcon />}
              onClick={() => executeQuery()}
              disabled={initialLoading || loading}
              sx={{ minHeight: 56, px: 2.2 }}
            >
              조회
            </Button>
            <Button
              variant="outlined"
              startIcon={<RestartAltRoundedIcon />}
              onClick={handleReset}
              disabled={initialLoading || loading}
              sx={{ minHeight: 56, px: 2.2 }}
            >
              초기화
            </Button>
          </Box>

          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mt: 1.4 }}>
            <Chip size="small" label={`선택 데이터셋: ${selectedDatasetLabel}`} />
            <Chip size="small" label={`원본: ${processFlowData?.sourceCollection ?? 'THISHMIDATA'}`} />
            <Chip size="small" label={`설비 그룹: ${selectedEquipment?.opstat_code_group ?? processFlowData?.opstatGroup ?? '-'}`} />
            <Chip
              size="small"
              label={`현재: ${processFlowData?.currentState?.label ?? '-'}`}
              sx={{ bgcolor: currentColors.bg, color: currentColors.text, fontWeight: 800 }}
            />
          </Box>
        </CardContent>
      </Card>

      <KpiGrid data={processFlowData} />

      <Card variant="outlined" sx={{ borderRadius: 2, borderColor: '#dce4ef' }}>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
            공정 흐름
          </Typography>
          <Divider sx={{ my: 1.2 }} />
          <ProcessFlowStrip data={processFlowData} />
        </CardContent>
      </Card>

      <Box
        sx={{
          display: 'grid',
          gap: 1.4,
          gridTemplateColumns: {
            xs: '1fr',
            xl: 'minmax(0, 2fr) 420px',
          },
          alignItems: 'start',
        }}
      >
        <Card variant="outlined" sx={{ borderRadius: 2, borderColor: '#dce4ef' }}>
          <CardContent>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              실시간 온도 추이
            </Typography>
            <Divider sx={{ my: 1.2 }} />
            {loading && !processFlowData ? (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 3 }}>
                <CircularProgress size={22} />
                <Typography variant="body2" color="text.secondary">
                  공정 데이터를 조회하는 중입니다...
                </Typography>
              </Box>
            ) : (
              <TemperatureTrendChart data={processFlowData} />
            )}
          </CardContent>
        </Card>

        <Card variant="outlined" sx={{ borderRadius: 2, borderColor: '#dce4ef' }}>
          <CardContent>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              현재 공정 상태 상세
            </Typography>
            <Divider sx={{ my: 1.2 }} />
            <DetailPanel data={processFlowData} />
          </CardContent>
        </Card>
      </Box>

      <Box
        sx={{
          display: 'grid',
          gap: 1.4,
          gridTemplateColumns: {
            xs: '1fr',
            xl: 'minmax(0, 2fr) 420px',
          },
          alignItems: 'start',
        }}
      >
        <Card variant="outlined" sx={{ borderRadius: 2, borderColor: '#dce4ef' }}>
          <CardContent>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              공정 단계별 평균 온도 / 체류시간
            </Typography>
            <Divider sx={{ my: 1.2 }} />
            <StageSummaryBars rows={processFlowData?.stageSummary ?? []} />
            <StageSummaryTable rows={processFlowData?.stageSummary ?? []} />
          </CardContent>
        </Card>

        <Card variant="outlined" sx={{ borderRadius: 2, borderColor: '#dce4ef' }}>
          <CardContent>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              이벤트 타임라인
            </Typography>
            <Divider sx={{ my: 1.2 }} />
            <EventTimeline events={processFlowData?.eventTimeline ?? []} />
          </CardContent>
        </Card>
      </Box>
    </Stack>
  );
}
