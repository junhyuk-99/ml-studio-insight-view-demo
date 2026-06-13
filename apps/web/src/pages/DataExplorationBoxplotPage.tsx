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
  FormControlLabel,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { dataExplorationService } from '../services/dataExplorationService';
import {
  buildInitialSelectedFields,
  isExplorationFieldDisabled,
  normalizeExplorationFieldOptions,
} from '../utils/dataExplorationFieldSelection';
import {
  filterDataExplorationDatasetOptions,
  formatAppliedMatchFilter,
  normalizeAppliedMatchFilter,
  persistDataExplorationDatasetKey,
  resolveAppliedMatchFilterMccode,
  resolveDataExplorationDatasetKeyWithFallback,
  resolveDatasetDisplayName,
} from '../utils/dataExplorationDataset';
import {
  getCurrentKstMonthKey,
  getCurrentKstMonthRange,
  resolveMonthRangeWithKstToday,
  shiftMonth,
} from '../utils/kstDate';
import type {
  BoxplotDataResponse,
  BoxplotFieldData,
  DataExplorationAppliedMatchFilter,
  DataExplorationDatasetOption,
  HistogramFieldOption,
} from '../types/dataExploration';

const DEFAULT_MAX_ROWS = 5000;
const MAX_ROWS = 50000;
const MAX_SELECTED_FIELDS = 12;
const MAX_OUTLIERS_PER_FIELD = 120;
const KST_OFFSET_HOURS = 9;

const BOX_COLORS = [
  '#f59e0b',
  '#0ea5e9',
  '#ef4444',
  '#6366f1',
  '#10b981',
  '#ec4899',
  '#8b5cf6',
  '#14b8a6',
  '#f97316',
  '#3b82f6',
  '#22c55e',
  '#e11d48',
];

type BoxplotGroupKey = 'time' | 'temperature' | 'pressure_motion' | 'other';

type BoxplotGroup = {
  key: BoxplotGroupKey;
  label: string;
  description: string;
  fields: BoxplotFieldData[];
};

const GROUP_META: Record<BoxplotGroupKey, { label: string; description: string }> = {
  time: {
    label: '시간 계열',
    description: 'Cycle/Time 계열 변수',
  },
  temperature: {
    label: '온도 계열',
    description: 'Temp 계열 변수',
  },
  pressure_motion: {
    label: '압력/속도/위치',
    description: 'Press/Velocity/Position 계열 변수',
  },
  other: {
    label: '기타',
    description: '기타 미분류 변수',
  },
};

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

function toKSTDate(utcIsoText: string | null | undefined): Date | null {
  if (!utcIsoText) {
    return null;
  }

  const utcDate = new Date(utcIsoText);
  if (Number.isNaN(utcDate.getTime())) {
    return null;
  }

  return new Date(utcDate.getTime() + KST_OFFSET_HOURS * 60 * 60 * 1000);
}

function toKSTDateInput(utcIsoText: string | null | undefined): string {
  const kstDate = toKSTDate(utcIsoText);
  if (!kstDate) {
    return '';
  }

  const year = kstDate.getUTCFullYear();
  const month = kstDate.getUTCMonth() + 1;
  const day = kstDate.getUTCDate();
  return `${year}-${pad2(month)}-${pad2(day)}`;
}

function parseDateInput(dateText: string): { year: number; month: number; day: number } | null {
  const normalized = dateText.trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(normalized)) {
    return null;
  }

  const [yearText, monthText, dayText] = normalized.split('-');
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);

  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) {
    return null;
  }
  if (month < 1 || month > 12 || day < 1 || day > 31) {
    return null;
  }

  return { year, month, day };
}

function toUtcStartIsoFromKstDate(dateText: string): string | null {
  const parsed = parseDateInput(dateText);
  if (!parsed) {
    return null;
  }

  const utcMillis = Date.UTC(parsed.year, parsed.month - 1, parsed.day, -KST_OFFSET_HOURS, 0, 0, 0);
  return new Date(utcMillis).toISOString();
}

function toUtcEndIsoFromKstDate(dateText: string): string | null {
  const parsed = parseDateInput(dateText);
  if (!parsed) {
    return null;
  }

  const nextDayUtcMillis = Date.UTC(
    parsed.year,
    parsed.month - 1,
    parsed.day + 1,
    -KST_OFFSET_HOURS,
    0,
    0,
    0,
  );
  return new Date(nextDayUtcMillis - 1).toISOString();
}

function formatDateRangeLabel(minTimestamp: string | null, maxTimestamp: string | null): string {
  const minDate = toKSTDateInput(minTimestamp);
  const maxDate = toKSTDateInput(maxTimestamp);
  if (!minDate || !maxDate) {
    return '-';
  }
  return `${minDate} ~ ${maxDate}`;
}

function formatMetric(value: number | null | undefined, digits = 3): string {
  if (value == null || Number.isNaN(value)) {
    return '-';
  }
  return value.toLocaleString('ko-KR', {
    maximumFractionDigits: digits,
    minimumFractionDigits: 0,
  });
}

function validateDateInputs(startDate: string, endDate: string): string | null {
  if (!startDate || !endDate) {
    return '시작일과 종료일을 모두 선택해 주세요.';
  }
  if (startDate > endDate) {
    return '시작일이 종료일보다 클 수 없습니다.';
  }
  if (!parseDateInput(startDate) || !parseDateInput(endDate)) {
    return '날짜 형식이 올바르지 않습니다.';
  }
  return null;
}

function getMonthKey(dateText: string): string {
  return dateText.slice(0, 7);
}

function formatMonthLabel(monthKey: string): string {
  if (!/^\d{4}-\d{2}$/.test(monthKey)) {
    return monthKey;
  }
  return `${monthKey.slice(0, 4)}년 ${monthKey.slice(5, 7)}월`;
}

function ellipsisLabel(value: string, limit = 14): string {
  if (value.length <= limit) {
    return value;
  }
  return `${value.slice(0, limit - 1)}…`;
}

function isFiniteNumber(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function classifyFieldGroup(fieldName: string): BoxplotGroupKey {
  const normalized = fieldName.toLowerCase();
  if (normalized.includes('temp')) {
    return 'temperature';
  }
  if (normalized.includes('time') || normalized.includes('cycle')) {
    return 'time';
  }
  if (
    normalized.includes('press') ||
    normalized.includes('velocity') ||
    normalized.includes('position') ||
    normalized.includes('rate') ||
    normalized.includes('rpm') ||
    normalized.includes('flow') ||
    normalized.includes('cushion')
  ) {
    return 'pressure_motion';
  }
  return 'other';
}

function hasValidBoxplotStats(row: BoxplotFieldData): boolean {
  return (
    row.sample_count > 0 &&
    isFiniteNumber(row.min) &&
    isFiniteNumber(row.q1) &&
    isFiniteNumber(row.median) &&
    isFiniteNumber(row.q3) &&
    isFiniteNumber(row.max) &&
    isFiniteNumber(row.whisker_low) &&
    isFiniteNumber(row.whisker_high)
  );
}

type RankedFieldStat = {
  field: string;
  value: number;
};

type BoxplotSummaryProps = {
  selectedCount: number;
  topOutliers: RankedFieldStat[];
  topVariability: RankedFieldStat[];
};

function BoxplotSummaryCards({ selectedCount, topOutliers, topVariability }: BoxplotSummaryProps) {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: {
          xs: '1fr',
          md: 'repeat(3, minmax(0, 1fr))',
        },
        gap: 1,
      }}
    >
      <Card variant="outlined" sx={{ borderColor: '#d7e1ee' }}>
        <CardContent sx={{ p: 1.4, '&:last-child': { pb: 1.4 } }}>
          <Typography variant="caption" color="text.secondary">
            선택 필드 수
          </Typography>
          <Typography variant="h6" sx={{ fontWeight: 800, mt: 0.2 }}>
            {selectedCount.toLocaleString('ko-KR')}개
          </Typography>
        </CardContent>
      </Card>

      <Card variant="outlined" sx={{ borderColor: '#d7e1ee' }}>
        <CardContent sx={{ p: 1.4, '&:last-child': { pb: 1.4 } }}>
          <Typography variant="caption" color="text.secondary">
            이상치 많은 필드 Top 3
          </Typography>
          <Box sx={{ mt: 0.8, display: 'flex', flexWrap: 'wrap', gap: 0.6 }}>
            {topOutliers.length === 0 && <Chip size="small" label="이상치 없음" variant="outlined" />}
            {topOutliers.map((row) => (
              <Chip
                key={`outlier-top-${row.field}`}
                size="small"
                label={`${ellipsisLabel(row.field, 18)} (${row.value.toLocaleString('ko-KR')})`}
              />
            ))}
          </Box>
        </CardContent>
      </Card>

      <Card variant="outlined" sx={{ borderColor: '#d7e1ee' }}>
        <CardContent sx={{ p: 1.4, '&:last-child': { pb: 1.4 } }}>
          <Typography variant="caption" color="text.secondary">
            변동성 큰 필드 Top 3 (IQR)
          </Typography>
          <Box sx={{ mt: 0.8, display: 'flex', flexWrap: 'wrap', gap: 0.6 }}>
            {topVariability.length === 0 && <Chip size="small" label="계산 불가" variant="outlined" />}
            {topVariability.map((row) => (
              <Chip
                key={`iqr-top-${row.field}`}
                size="small"
                label={`${ellipsisLabel(row.field, 18)} (${formatMetric(row.value, 3)})`}
              />
            ))}
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}

type BoxplotComparisonChartProps = {
  fields: BoxplotFieldData[];
};

function BoxplotComparisonChart({ fields }: BoxplotComparisonChartProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState<{ x: number; y: number } | null>(null);

  const validFields = useMemo(() => fields.filter(hasValidBoxplotStats), [fields]);

  if (validFields.length === 0) {
    return <Alert severity="info">선택된 그룹에 유효한 수치 데이터가 없습니다.</Alert>;
  }

  let rawMin = Number.POSITIVE_INFINITY;
  let rawMax = Number.NEGATIVE_INFINITY;
  for (const row of validFields) {
    rawMin = Math.min(rawMin, row.min as number);
    rawMax = Math.max(rawMax, row.max as number);
  }

  if (!Number.isFinite(rawMin) || !Number.isFinite(rawMax)) {
    return <Alert severity="info">박스플롯 축 범위를 계산할 수 없습니다.</Alert>;
  }

  const fieldCount = validFields.length;
  const width = Math.max(740, fieldCount * 122);
  const height = 430;
  const margin = {
    top: 26,
    right: 24,
    bottom: 132,
    left: 80,
  };
  const plotWidth = width - margin.left - margin.right;
  const plotHeight = height - margin.top - margin.bottom;
  const groupWidth = plotWidth / fieldCount;
  const boxWidth = Math.min(56, Math.max(18, groupWidth * 0.52));

  let yMin = rawMin;
  let yMax = rawMax;
  if (Math.abs(yMax - yMin) < 1e-9) {
    const spread = Math.max(Math.abs(yMax), 1) * 0.02;
    yMin = yMin - spread;
    yMax = yMax + spread;
  }

  const yRange = yMax - yMin;
  const toY = (value: number) => margin.top + (1 - (value - yMin) / yRange) * plotHeight;

  const yTickCount = 5;
  const yTicks = Array.from({ length: yTickCount + 1 }).map((_, index) => {
    const ratio = index / yTickCount;
    const value = yMin + yRange * ratio;
    return {
      value,
      y: margin.top + (1 - ratio) * plotHeight,
    };
  });

  const hoveredField =
    hoveredIndex == null ? null : validFields[Math.max(0, Math.min(hoveredIndex, validFields.length - 1))];

  return (
    <Box ref={containerRef} sx={{ position: 'relative', width: '100%', overflowX: 'auto' }}>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        width={width}
        role="img"
        aria-label="선택 필드 박스플롯 비교 차트"
      >
        {yTicks.map((tick, index) => (
          <g key={`y-tick-${index}`}>
            <line
              x1={margin.left}
              y1={tick.y}
              x2={width - margin.right}
              y2={tick.y}
              stroke="#e7edf5"
              strokeWidth={1}
            />
            <text
              x={margin.left - 10}
              y={tick.y + 4}
              textAnchor="end"
              fontSize={11}
              fill="#607089"
            >
              {formatMetric(tick.value, 2)}
            </text>
          </g>
        ))}

        <line
          x1={margin.left}
          y1={margin.top}
          x2={margin.left}
          y2={height - margin.bottom}
          stroke="#94a3b8"
          strokeWidth={1.1}
        />
        <line
          x1={margin.left}
          y1={height - margin.bottom}
          x2={width - margin.right}
          y2={height - margin.bottom}
          stroke="#94a3b8"
          strokeWidth={1.1}
        />

        {validFields.map((row, index) => {
          const centerX = margin.left + index * groupWidth + groupWidth / 2;
          const boxColor = BOX_COLORS[index % BOX_COLORS.length];

          const yWhiskerLow = toY(row.whisker_low as number);
          const yWhiskerHigh = toY(row.whisker_high as number);
          const yQ1 = toY(row.q1 as number);
          const yQ3 = toY(row.q3 as number);
          const yMedian = toY(row.median as number);

          const boxTop = Math.min(yQ1, yQ3);
          const boxHeight = Math.max(1, Math.abs(yQ1 - yQ3));
          const capWidth = boxWidth * 0.56;

          return (
            <g key={`boxplot-${row.field}`}>
              <line
                x1={centerX}
                y1={yWhiskerHigh}
                x2={centerX}
                y2={yWhiskerLow}
                stroke={boxColor}
                strokeWidth={1.8}
              />
              <line
                x1={centerX - capWidth / 2}
                y1={yWhiskerHigh}
                x2={centerX + capWidth / 2}
                y2={yWhiskerHigh}
                stroke={boxColor}
                strokeWidth={1.8}
              />
              <line
                x1={centerX - capWidth / 2}
                y1={yWhiskerLow}
                x2={centerX + capWidth / 2}
                y2={yWhiskerLow}
                stroke={boxColor}
                strokeWidth={1.8}
              />

              <rect
                x={centerX - boxWidth / 2}
                y={boxTop}
                width={boxWidth}
                height={boxHeight}
                fill={boxColor}
                fillOpacity={0.32}
                stroke={boxColor}
                strokeWidth={2}
                rx={4}
              />

              <line
                x1={centerX - boxWidth / 2}
                y1={yMedian}
                x2={centerX + boxWidth / 2}
                y2={yMedian}
                stroke="#111827"
                strokeWidth={2.4}
              />

              {row.outliers.map((outlierValue, outlierIndex) => {
                const jitter = ((outlierIndex % 5) - 2) * 2;
                return (
                  <circle
                    key={`${row.field}-outlier-${outlierIndex}`}
                    cx={centerX + jitter}
                    cy={toY(outlierValue)}
                    r={2.5}
                    fill="#111827"
                    opacity={0.82}
                  />
                );
              })}

              <rect
                x={centerX - groupWidth / 2}
                y={margin.top}
                width={groupWidth}
                height={plotHeight}
                fill="transparent"
                onMouseMove={(event) => {
                  const rect = containerRef.current?.getBoundingClientRect();
                  if (!rect) {
                    return;
                  }
                  setHoveredIndex(index);
                  setTooltipPosition({
                    x: event.clientX - rect.left + 12,
                    y: event.clientY - rect.top + 12,
                  });
                }}
                onMouseLeave={() => {
                  setHoveredIndex(null);
                  setTooltipPosition(null);
                }}
              />

              <text
                x={centerX}
                y={height - margin.bottom + 26}
                textAnchor="end"
                transform={`rotate(-42 ${centerX} ${height - margin.bottom + 26})`}
                fontSize={11}
                fill="#334155"
              >
                {ellipsisLabel(row.field)}
              </text>
            </g>
          );
        })}

        <text
          x={margin.left + plotWidth / 2}
          y={height - 16}
          textAnchor="middle"
          fontSize={12}
          fontWeight={700}
          fill="#41546f"
        >
          센서 필드
        </text>

        <text
          x={22}
          y={margin.top + plotHeight / 2}
          textAnchor="middle"
          fontSize={12}
          fontWeight={700}
          fill="#41546f"
          transform={`rotate(-90 22 ${margin.top + plotHeight / 2})`}
        >
          값
        </text>
      </svg>

      {hoveredField && tooltipPosition && (
        <Box
          sx={{
            position: 'absolute',
            left: tooltipPosition.x,
            top: tooltipPosition.y,
            minWidth: 240,
            maxWidth: 340,
            p: 1,
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1.5,
            backgroundColor: 'rgba(255, 255, 255, 0.96)',
            boxShadow: '0 2px 10px rgba(15, 23, 42, 0.14)',
            pointerEvents: 'none',
            zIndex: 2,
          }}
        >
          <Typography variant="caption" display="block" sx={{ fontWeight: 800 }}>
            {hoveredField.field}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            sample_count: {hoveredField.sample_count.toLocaleString('ko-KR')}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            min: {formatMetric(hoveredField.min, 3)}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            Q1: {formatMetric(hoveredField.q1, 3)}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            median: {formatMetric(hoveredField.median, 3)}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            Q3: {formatMetric(hoveredField.q3, 3)}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            max: {formatMetric(hoveredField.max, 3)}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            outlier_count: {hoveredField.outlier_count.toLocaleString('ko-KR')}
          </Typography>
        </Box>
      )}
    </Box>
  );
}

export function DataExplorationBoxplotPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [initialKstMonthRange] = useState(() => getCurrentKstMonthRange());
  const [startDate, setStartDate] = useState(initialKstMonthRange.startDate);
  const [endDate, setEndDate] = useState(initialKstMonthRange.endDate);
  const [maxRows, setMaxRows] = useState(DEFAULT_MAX_ROWS);
  const [allDataMode, setAllDataMode] = useState(false);
  const [activeMonth, setActiveMonth] = useState(initialKstMonthRange.monthKey);
  const [datasetOptions, setDatasetOptions] = useState<DataExplorationDatasetOption[]>([]);
  const [selectedDatasetKey, setSelectedDatasetKey] = useState('');
  const [equipmentIdFilter, setEquipmentIdFilter] = useState('');
  const [appliedMatchFilter, setAppliedMatchFilter] = useState<DataExplorationAppliedMatchFilter>({});

  const [minTimestamp, setMinTimestamp] = useState<string | null>(null);
  const [maxTimestamp, setMaxTimestamp] = useState<string | null>(null);
  const [fieldOptions, setFieldOptions] = useState<HistogramFieldOption[]>([]);
  const [selectedFields, setSelectedFields] = useState<string[]>([]);
  const [defaultSelectedFields, setDefaultSelectedFields] = useState<string[]>([]);
  const [groupByOptions, setGroupByOptions] = useState<string[]>([]);
  const [selectedGroupBy, setSelectedGroupBy] = useState('');
  const [activeGroupKey, setActiveGroupKey] = useState<BoxplotGroupKey | ''>('');

  const [boxplotData, setBoxplotData] = useState<BoxplotDataResponse | null>(null);

  const [datasetLoading, setDatasetLoading] = useState(false);
  const [fieldLoading, setFieldLoading] = useState(false);
  const [queryLoading, setQueryLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [pageInfo, setPageInfo] = useState<string | null>(null);
  const [selectionWarning, setSelectionWarning] = useState<string | null>(null);

  const initialLoadStartedRef = useRef(false);
  const fieldRequestIdRef = useRef(0);
  const queryRequestIdRef = useRef(0);

  const queryDatasetKey = useMemo(
    () => searchParams.get('datasetKey')?.trim() || searchParams.get('dataset_key')?.trim() || '',
    [searchParams],
  );

  const selectedFieldSet = useMemo(() => new Set(selectedFields), [selectedFields]);
  const disabledFieldSet = useMemo(
    () =>
      new Set(
        fieldOptions.filter((fieldOption) => isExplorationFieldDisabled(fieldOption)).map((field) => field.field),
      ),
    [fieldOptions],
  );

  const orderedBoxplotFields = useMemo(() => {
    if (!boxplotData) {
      return [] as BoxplotFieldData[];
    }

    if (selectedGroupBy) {
      return boxplotData.field_boxplots;
    }

    const fieldMap = new Map(boxplotData.field_boxplots.map((row) => [row.field, row]));
    return selectedFields
      .map((field) => fieldMap.get(field))
      .filter((row): row is BoxplotFieldData => Boolean(row));
  }, [boxplotData, selectedFields, selectedGroupBy]);

  const groupedBoxplotFields = useMemo(() => {
    const grouped: Record<BoxplotGroupKey, BoxplotFieldData[]> = {
      time: [],
      temperature: [],
      pressure_motion: [],
      other: [],
    };

    for (const fieldRow of orderedBoxplotFields) {
      grouped[classifyFieldGroup(fieldRow.field)].push(fieldRow);
    }

    const groups: BoxplotGroup[] = (Object.keys(GROUP_META) as BoxplotGroupKey[])
      .map((key) => ({
        key,
        label: GROUP_META[key].label,
        description: GROUP_META[key].description,
        fields: grouped[key],
      }))
      .filter((group) => group.fields.length > 0);

    return groups;
  }, [orderedBoxplotFields]);

  useEffect(() => {
    if (groupedBoxplotFields.length === 0) {
      setActiveGroupKey('');
      return;
    }

    if (!activeGroupKey || !groupedBoxplotFields.some((group) => group.key === activeGroupKey)) {
      setActiveGroupKey(groupedBoxplotFields[0].key);
    }
  }, [groupedBoxplotFields, activeGroupKey]);

  const activeGroup = useMemo(() => {
    if (groupedBoxplotFields.length === 0) {
      return null;
    }
    return groupedBoxplotFields.find((group) => group.key === activeGroupKey) ?? groupedBoxplotFields[0];
  }, [groupedBoxplotFields, activeGroupKey]);

  const minDateText = useMemo(() => toKSTDateInput(minTimestamp), [minTimestamp]);
  const maxDateText = useMemo(() => toKSTDateInput(maxTimestamp), [maxTimestamp]);
  const selectedDatasetLabel = useMemo(() => {
    const selected = datasetOptions.find((option) => option.datasetKey === selectedDatasetKey);
    return selected ? resolveDatasetDisplayName(selected) : selectedDatasetKey || '-';
  }, [datasetOptions, selectedDatasetKey]);
  const selectedDatasetMccode = useMemo(
    () => resolveAppliedMatchFilterMccode(appliedMatchFilter),
    [appliedMatchFilter],
  );

  const syncDatasetKeyQuery = useCallback(
    (datasetKey: string) => {
      const normalized = datasetKey.trim();
      const nextParams = new URLSearchParams(searchParams);
      if (normalized) {
        nextParams.set('datasetKey', normalized);
      } else {
        nextParams.delete('datasetKey');
      }
      nextParams.delete('dataset_key');
      setSearchParams(nextParams, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const topOutliers = useMemo(() => {
    return orderedBoxplotFields
      .map((row) => ({
        field: row.field,
        value: row.outlier_count ?? 0,
      }))
      .filter((row) => row.value > 0)
      .sort((left, right) => right.value - left.value)
      .slice(0, 3);
  }, [orderedBoxplotFields]);

  const topVariability = useMemo(() => {
    return orderedBoxplotFields
      .map((row) => {
        const iqr =
          isFiniteNumber(row.q1) && isFiniteNumber(row.q3) ? Math.max(0, row.q3 - row.q1) : Number.NaN;
        return {
          field: row.field,
          value: iqr,
        };
      })
      .filter((row) => Number.isFinite(row.value) && row.value > 0)
      .sort((left, right) => right.value - left.value)
      .slice(0, 3);
  }, [orderedBoxplotFields]);

  const runBoxplotQuery = useCallback(
    async (
      datasetKey: string,
      queryStartDate: string,
      queryEndDate: string,
      queryFields: string[],
      queryEquipmentId: string,
      queryMaxRows: number,
      queryGroupBy: string,
    ) => {
      const requestId = ++queryRequestIdRef.current;
      if (!datasetKey.trim()) {
        if (requestId === queryRequestIdRef.current) {
          setPageError(null);
          setPageInfo('데이터셋을 선택해 주세요.');
          setBoxplotData(null);
          setQueryLoading(false);
        }
        return;
      }
      const normalizedEquipmentId = queryEquipmentId.trim().toUpperCase();
      if (
        normalizedEquipmentId &&
        selectedDatasetMccode &&
        normalizedEquipmentId !== selectedDatasetMccode.toUpperCase()
      ) {
        if (requestId === queryRequestIdRef.current) {
          setPageError(`선택한 dataset은 ${selectedDatasetMccode}인데 입력 MCCODE는 ${normalizedEquipmentId}입니다.`);
          setPageInfo(null);
          setBoxplotData(null);
          setQueryLoading(false);
        }
        return;
      }
      const dateValidationMessage = validateDateInputs(queryStartDate, queryEndDate);
      if (dateValidationMessage) {
        if (requestId === queryRequestIdRef.current) {
          setPageError(dateValidationMessage);
          setPageInfo(null);
          setBoxplotData(null);
          setQueryLoading(false);
        }
        return;
      }

      if (queryFields.length === 0) {
        if (requestId === queryRequestIdRef.current) {
          setPageError(null);
          setPageInfo('필드를 선택해 주세요.');
          setBoxplotData(null);
          setQueryLoading(false);
        }
        return;
      }

      const fromIso = toUtcStartIsoFromKstDate(queryStartDate);
      const toIso = toUtcEndIsoFromKstDate(queryEndDate);
      if (!fromIso || !toIso) {
        if (requestId === queryRequestIdRef.current) {
          setPageError('날짜 변환에 실패했습니다. 날짜를 다시 선택해 주세요.');
          setPageInfo(null);
          setBoxplotData(null);
          setQueryLoading(false);
        }
        return;
      }

      setQueryLoading(true);
      setPageError(null);
      setPageInfo(null);

      try {
        const response = await dataExplorationService.getBoxplotData({
          dataset_key: datasetKey,
          equipment_id: queryEquipmentId.trim() || undefined,
          from: fromIso,
          to: toIso,
          selected_fields: queryFields,
          max_rows: queryMaxRows,
          group_by: queryGroupBy || undefined,
          max_outliers_per_field: MAX_OUTLIERS_PER_FIELD,
        });

        if (requestId !== queryRequestIdRef.current) {
          return;
        }

        setBoxplotData(response);
        setAppliedMatchFilter(normalizeAppliedMatchFilter(response.applied_match_filter));

        if (response.total_row_count === 0) {
          setPageInfo('조회된 데이터가 없습니다.');
          return;
        }

        if (response.sampling_applied) {
          setPageInfo(
            `데이터가 많아 ${response.sampling_step.toLocaleString('ko-KR')}건 간격으로 샘플링하여 ${response.sampled_row_count.toLocaleString('ko-KR')}건을 계산했습니다.`,
          );
          return;
        }

        setPageInfo(null);
      } catch (error: unknown) {
        if (requestId !== queryRequestIdRef.current) {
          return;
        }

        setBoxplotData(null);
        setPageError(error instanceof Error ? error.message : '박스플롯 조회에 실패했습니다.');
      } finally {
        if (requestId === queryRequestIdRef.current) {
          setQueryLoading(false);
        }
      }
    },
    [selectedDatasetMccode],
  );

  const applyMonthRange = useCallback(
    (monthKey: string) => {
      const resolved = resolveMonthRangeWithKstToday(monthKey);
      setStartDate(resolved.startDate);
      setEndDate(resolved.endDate);
      setActiveMonth(monthKey);
      setAllDataMode(false);
    },
    [],
  );

  const applyAllDataRange = useCallback(() => {
    if (!minDateText || !maxDateText) {
      return;
    }

    setStartDate(minDateText);
    setEndDate(maxDateText);
    setAllDataMode(true);
  }, [minDateText, maxDateText]);

  const loadFieldOptions = useCallback(async (datasetKey: string, runInitialQuery: boolean) => {
    const requestId = ++fieldRequestIdRef.current;

    if (!datasetKey.trim()) {
      setFieldOptions([]);
      setSelectedFields([]);
      setDefaultSelectedFields([]);
      setGroupByOptions([]);
      setSelectedGroupBy('');
      setAppliedMatchFilter({});
      setBoxplotData(null);
      setPageInfo('데이터셋을 선택해 주세요.');
      return;
    }

    setFieldLoading(true);
    setPageError(null);
    setSelectionWarning(null);

    try {
      const fromIso = toUtcStartIsoFromKstDate(startDate);
      const toIso = toUtcEndIsoFromKstDate(endDate);
      const response = await dataExplorationService.getBoxplotFields({
        datasetKey,
        from: fromIso ?? undefined,
        to: toIso ?? undefined,
        equipmentId: equipmentIdFilter.trim() || undefined,
      });

      if (requestId !== fieldRequestIdRef.current) {
        return;
      }

      setMinTimestamp(response.min_timestamp);
      setMaxTimestamp(response.max_timestamp);
      const normalizedFieldOptions = normalizeExplorationFieldOptions(response.fields ?? []);
      setFieldOptions(normalizedFieldOptions);
      setAppliedMatchFilter(normalizeAppliedMatchFilter(response.applied_match_filter));
      const nextGroupByOptions = response.group_by_fields ?? [];
      setGroupByOptions(nextGroupByOptions);
      const nextGroupBy = nextGroupByOptions.includes(selectedGroupBy)
        ? selectedGroupBy
        : response.default_group_by || nextGroupByOptions[0] || '';
      setSelectedGroupBy(nextGroupBy);

      const resolvedMinDate = toKSTDateInput(response.min_timestamp);
      const resolvedMaxDate = toKSTDateInput(response.max_timestamp);
      const initialSelectedFields = buildInitialSelectedFields(
        normalizedFieldOptions,
        response.default_selected_fields,
        MAX_SELECTED_FIELDS,
      );

      setDefaultSelectedFields(initialSelectedFields);
      setSelectedFields(initialSelectedFields);
      setMaxRows(DEFAULT_MAX_ROWS);

      if (allDataMode && resolvedMinDate && resolvedMaxDate) {
        setStartDate(resolvedMinDate);
        setEndDate(resolvedMaxDate);
      }

      if (response.row_count === 0) {
        setPageInfo('조회된 데이터가 없습니다.');
        setBoxplotData(null);
        return;
      }

      if (initialSelectedFields.length === 0) {
        setPageInfo('필드를 선택해 주세요.');
        setBoxplotData(null);
        return;
      }

      if (runInitialQuery) {
        const queryStartDate = allDataMode && resolvedMinDate ? resolvedMinDate : startDate;
        const queryEndDate = allDataMode && resolvedMaxDate ? resolvedMaxDate : endDate;

        await runBoxplotQuery(
          datasetKey,
          queryStartDate,
          queryEndDate,
          initialSelectedFields,
          equipmentIdFilter,
          DEFAULT_MAX_ROWS,
          nextGroupBy,
        );
      } else {
        setBoxplotData(null);
        setPageInfo('데이터셋이 변경되었습니다. 필드를 확인한 뒤 조회를 실행해 주세요.');
      }
    } catch (error: unknown) {
      if (requestId !== fieldRequestIdRef.current) {
        return;
      }

      setPageError(error instanceof Error ? error.message : '박스플롯 초기 정보를 불러오지 못했습니다.');
    } finally {
      if (requestId === fieldRequestIdRef.current) {
        setFieldLoading(false);
      }
    }
  }, [allDataMode, endDate, equipmentIdFilter, runBoxplotQuery, selectedGroupBy, startDate]);

  const loadDatasetsAndInitialize = useCallback(async () => {
    setDatasetLoading(true);
    setPageError(null);

    try {
      const datasets = filterDataExplorationDatasetOptions(await dataExplorationService.getDatasets());
      setDatasetOptions(datasets);

      const resolved = resolveDataExplorationDatasetKeyWithFallback({
        datasetOptions: datasets,
        queryDatasetKey,
        preferredDatasetKey: selectedDatasetKey,
      });
      const initialDatasetKey = resolved.datasetKey;
      if (!initialDatasetKey) {
        setSelectedDatasetKey('');
        setFieldOptions([]);
        setSelectedFields([]);
        setDefaultSelectedFields([]);
        setGroupByOptions([]);
        setSelectedGroupBy('');
        setAppliedMatchFilter({});
        setBoxplotData(null);
        setPageInfo('조회 가능한 데이터셋이 없습니다.');
        return;
      }

      setSelectedDatasetKey(initialDatasetKey);
      persistDataExplorationDatasetKey(initialDatasetKey);
      syncDatasetKeyQuery(initialDatasetKey);
      await loadFieldOptions(initialDatasetKey, true);
      if (resolved.warning) {
        setSelectionWarning(resolved.warning);
      }
    } catch (error: unknown) {
      setPageError(error instanceof Error ? error.message : '데이터셋 목록을 불러오지 못했습니다.');
    } finally {
      setDatasetLoading(false);
    }
  }, [loadFieldOptions, queryDatasetKey, selectedDatasetKey, syncDatasetKeyQuery]);

  useEffect(() => {
    if (initialLoadStartedRef.current) {
      return;
    }

    initialLoadStartedRef.current = true;
    void loadDatasetsAndInitialize();
  }, [loadDatasetsAndInitialize]);

  const handleSearch = () => {
    void runBoxplotQuery(
      selectedDatasetKey,
      startDate,
      endDate,
      selectedFields,
      equipmentIdFilter,
      maxRows,
      selectedGroupBy,
    );
  };

  const handleReset = () => {
    const currentMonthRange = getCurrentKstMonthRange();

    setMaxRows(DEFAULT_MAX_ROWS);
    setSelectedFields(defaultSelectedFields);
    setSelectionWarning(null);
    setAllDataMode(false);
    setActiveMonth(currentMonthRange.monthKey);
    setStartDate(currentMonthRange.startDate);
    setEndDate(currentMonthRange.endDate);

    if (defaultSelectedFields.length === 0) {
      setBoxplotData(null);
      setPageInfo('필드를 선택해 주세요.');
      return;
    }

    void runBoxplotQuery(
      selectedDatasetKey,
      currentMonthRange.startDate,
      currentMonthRange.endDate,
      defaultSelectedFields,
      equipmentIdFilter,
      DEFAULT_MAX_ROWS,
      selectedGroupBy,
    );
  };

  const handleDatasetChange = (nextDatasetKey: string) => {
    const normalized = nextDatasetKey.trim();
    if (!normalized || normalized === selectedDatasetKey) {
      return;
    }

    setSelectedDatasetKey(normalized);
    persistDataExplorationDatasetKey(normalized);
    syncDatasetKeyQuery(normalized);
    setSelectionWarning(null);
    setAppliedMatchFilter({});
    setBoxplotData(null);
    setGroupByOptions([]);
    setSelectedGroupBy('');
    setPageInfo('데이터셋이 변경되었습니다. 필드를 다시 불러오는 중입니다.');
    void loadFieldOptions(normalized, false);
  };

  const handleGroupByChange = (groupBy: string) => {
    setSelectedGroupBy(groupBy);
    setBoxplotData(null);
    setPageInfo('groupBy가 변경되었습니다. 조회를 실행해 주세요.');
  };

  const handleToggleField = (fieldName: string, checked: boolean) => {
    if (disabledFieldSet.has(fieldName)) {
      return;
    }

    setSelectionWarning(null);

    setSelectedFields((previous) => {
      if (checked) {
        if (previous.includes(fieldName)) {
          return previous;
        }
        if (previous.length >= MAX_SELECTED_FIELDS) {
          setSelectionWarning(`최대 ${MAX_SELECTED_FIELDS}개 필드까지 선택할 수 있습니다.`);
          return previous;
        }
        return [...previous, fieldName];
      }

      return previous.filter((field) => field !== fieldName);
    });
  };

  const handleMaxRowsChange = (value: string) => {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      setMaxRows(DEFAULT_MAX_ROWS);
      return;
    }
    const normalized = Math.min(MAX_ROWS, Math.max(100, Math.floor(parsed)));
    setMaxRows(normalized);
  };

  const handleAllDataModeChange = (checked: boolean) => {
    setAllDataMode(checked);

    if (checked) {
      applyAllDataRange();
      return;
    }

    applyMonthRange(activeMonth || getCurrentKstMonthKey());
  };

  const moveMonth = (direction: -1 | 1) => {
    if (!activeMonth) {
      return;
    }

    const nextMonth = shiftMonth(activeMonth, direction);
    if (direction > 0 && nextMonth > getCurrentKstMonthKey()) {
      return;
    }

    applyMonthRange(nextMonth);
  };

  const canMovePrevMonth = useMemo(() => {
    return Boolean(activeMonth) && !allDataMode;
  }, [activeMonth, allDataMode]);

  const canMoveNextMonth = useMemo(() => {
    if (!activeMonth || allDataMode) {
      return false;
    }
    return shiftMonth(activeMonth, 1) <= getCurrentKstMonthKey();
  }, [activeMonth, allDataMode]);

  const selectedRangeLabel = useMemo(() => {
    if (!startDate || !endDate) {
      return '-';
    }
    return `${startDate} ~ ${endDate}`;
  }, [startDate, endDate]);
  const equipmentConflictMessage = useMemo(() => {
    const normalizedEquipmentId = equipmentIdFilter.trim().toUpperCase();
    if (!normalizedEquipmentId || !selectedDatasetMccode) {
      return null;
    }
    if (normalizedEquipmentId === selectedDatasetMccode.toUpperCase()) {
      return null;
    }
    return `선택한 dataset은 ${selectedDatasetMccode}인데 입력 MCCODE는 ${normalizedEquipmentId}입니다.`;
  }, [equipmentIdFilter, selectedDatasetMccode]);

  return (
    <Stack spacing={2.2}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          박스플롯
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.7 }}>
          원본 데이터(`선택한 데이터셋`)에서 선택한 센서 컬럼별 분포와 이상치를 비교합니다.
        </Typography>
      </Box>

      {pageError && <Alert severity="error">{pageError}</Alert>}
      {selectionWarning && <Alert severity="warning">{selectionWarning}</Alert>}
      {pageInfo && !pageError && <Alert severity="info">{pageInfo}</Alert>}

      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
            조회 조건
          </Typography>
          <Divider sx={{ my: 1.2 }} />

          <Stack spacing={1.4}>
            <Box
              sx={{
                display: 'flex',
                flexWrap: 'wrap',
                alignItems: 'center',
                gap: 1,
              }}
            >
              <FormControlLabel
                control={
                  <Checkbox
                    checked={allDataMode}
                    onChange={(event) => handleAllDataModeChange(event.target.checked)}
                  />
                }
                label="전체 데이터 조회"
                sx={{ mr: 1 }}
              />

              <Button
                variant="outlined"
                size="small"
                onClick={() => moveMonth(-1)}
                disabled={!canMovePrevMonth}
              >
                이전 월
              </Button>

              <Chip
                color={allDataMode ? 'default' : 'primary'}
                variant={allDataMode ? 'outlined' : 'filled'}
                label={allDataMode ? '전체 범위' : formatMonthLabel(activeMonth)}
              />

              <Button
                variant="outlined"
                size="small"
                onClick={() => moveMonth(1)}
                disabled={!canMoveNextMonth}
              >
                다음 월
              </Button>

              <Typography variant="body2" color="text.secondary" sx={{ ml: { md: 'auto' } }}>
                현재 조회 범위: {selectedRangeLabel}
              </Typography>
            </Box>

            <Box
              sx={{
                display: 'grid',
                gap: 1.2,
                gridTemplateColumns: {
                  xs: '1fr',
                  md: 'repeat(7, minmax(0, 1fr))',
                },
              }}
            >
              <TextField
                select
                label="데이터셋"
                value={selectedDatasetKey}
                onChange={(event) => handleDatasetChange(event.target.value)}
                disabled={datasetLoading || fieldLoading || queryLoading || datasetOptions.length === 0}
                fullWidth
              >
                {datasetOptions.map((option) => (
                  <MenuItem key={option.datasetKey} value={option.datasetKey}>
                    {resolveDatasetDisplayName(option)}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label="MCCODE"
                placeholder="예: DEMO-MC-001"
                value={equipmentIdFilter}
                onChange={(event) => setEquipmentIdFilter(event.target.value)}
                error={Boolean(equipmentConflictMessage)}
                helperText={
                  equipmentConflictMessage ??
                  (selectedDatasetMccode ? `dataset 적용 MCCODE: ${selectedDatasetMccode}` : 'dataset match_filter 기준으로 조회됩니다.')
                }
                fullWidth
              />
              <TextField
                select
                label="groupBy"
                value={selectedGroupBy}
                onChange={(event) => handleGroupByChange(event.target.value)}
                disabled={datasetLoading || fieldLoading || queryLoading || groupByOptions.length === 0}
                fullWidth
              >
                {groupByOptions.length === 0 && <MenuItem value="">선택 불가</MenuItem>}
                {groupByOptions.map((option) => (
                  <MenuItem key={option} value={option}>
                    {option}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label="시작일"
                type="date"
                value={startDate}
                onChange={(event) => {
                  setStartDate(event.target.value);
                  setAllDataMode(false);
                  if (event.target.value) {
                    setActiveMonth(getMonthKey(event.target.value));
                  }
                }}
                InputLabelProps={{ shrink: true }}
                fullWidth
              />
              <TextField
                label="종료일"
                type="date"
                value={endDate}
                onChange={(event) => {
                  setEndDate(event.target.value);
                  setAllDataMode(false);
                  if (event.target.value) {
                    setActiveMonth(getMonthKey(event.target.value));
                  }
                }}
                InputLabelProps={{ shrink: true }}
                fullWidth
              />
              <TextField
                label="Max rows"
                type="number"
                value={maxRows}
                onChange={(event) => handleMaxRowsChange(event.target.value)}
                inputProps={{
                  min: 100,
                  max: MAX_ROWS,
                }}
                fullWidth
              />
              <Button
                variant="contained"
                onClick={handleSearch}
                disabled={datasetLoading || fieldLoading || queryLoading || !selectedDatasetKey}
                sx={{ minHeight: 56 }}
              >
                {queryLoading ? '조회 중...' : '조회'}
              </Button>
              <Button
                variant="outlined"
                onClick={handleReset}
                disabled={datasetLoading || fieldLoading || queryLoading}
                sx={{ minHeight: 56 }}
              >
                초기화
              </Button>
            </Box>

            <Typography variant="body2" color="text.secondary">
              원본 데이터 범위(KST): {formatDateRangeLabel(minTimestamp, maxTimestamp)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              선택 데이터셋: {selectedDatasetLabel}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              적용 필터: {formatAppliedMatchFilter(appliedMatchFilter)}
            </Typography>
          </Stack>
        </CardContent>
      </Card>

      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Box
            sx={{
              display: 'grid',
              gap: 2,
              gridTemplateColumns: {
                xs: '1fr',
                lg: '300px minmax(0, 1fr)',
              },
              alignItems: 'start',
            }}
          >
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                필드 선택
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.4 }}>
                기본 선택은 값이 있는 PV/SV 컬럼 중심으로 자동 적용되며, 필요 시 수동 변경할 수 있습니다.
              </Typography>
              <Divider sx={{ my: 1.2 }} />

              <Box
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 1.5,
                  p: 1,
                  maxHeight: {
                    xs: 260,
                    lg: 720,
                  },
                  overflowY: 'auto',
                  backgroundColor: '#fbfdff',
                }}
              >
                {fieldLoading && (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 0.8, py: 1.2 }}>
                    <CircularProgress size={18} />
                    <Typography variant="body2" color="text.secondary">
                      필드 목록을 불러오는 중입니다...
                    </Typography>
                  </Box>
                )}

                {!fieldLoading && fieldOptions.length === 0 && (
                  <Alert severity="info">선택 가능한 수치형 필드가 없습니다.</Alert>
                )}

                {!fieldLoading &&
                  fieldOptions.map((option) => {
                    const optionDisabled = isExplorationFieldDisabled(option);
                    return (
                    <FormControlLabel
                      key={option.field}
                      control={
                        <Checkbox
                          checked={selectedFieldSet.has(option.field)}
                          onChange={(event) =>
                            handleToggleField(option.field, event.target.checked)
                          }
                          disabled={optionDisabled}
                          size="small"
                        />
                      }
                      label={
                        <Box sx={{ minWidth: 0 }}>
                          <Typography
                            variant="body2"
                            sx={{
                              wordBreak: 'break-all',
                              fontWeight: option.sequence_field ? 500 : 700,
                            }}
                          >
                            {option.field}
                          </Typography>
                          {optionDisabled && (
                            <Typography variant="caption" color="text.secondary">
                              NULL-only (현재 조회 범위)
                            </Typography>
                          )}
                          {option.sequence_field && (
                            <Typography variant="caption" color="text.secondary">
                              sequence candidate
                            </Typography>
                          )}
                        </Box>
                      }
                      sx={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        m: 0,
                        px: 0.5,
                        py: 0.2,
                        width: '100%',
                      }}
                    />
                    );
                  })}
              </Box>
            </Box>

            <Box sx={{ minWidth: 0 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                박스플롯 시각화
              </Typography>
              <Divider sx={{ my: 1.2 }} />

              {queryLoading && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 1 }}>
                  <CircularProgress size={20} />
                  <Typography variant="body2" color="text.secondary">
                    박스플롯을 계산하는 중입니다...
                  </Typography>
                </Box>
              )}

              {!queryLoading && selectedFields.length === 0 && (
                <Alert severity="info">필드를 선택해 주세요.</Alert>
              )}

              {!queryLoading &&
                selectedFields.length > 0 &&
                boxplotData &&
                boxplotData.total_row_count === 0 && (
                  <Alert severity="info">조회된 데이터가 없습니다.</Alert>
                )}

              {!queryLoading &&
                selectedFields.length > 0 &&
                boxplotData &&
                boxplotData.total_row_count > 0 && (
                  <Stack spacing={1.2}>
                    <BoxplotSummaryCards
                      selectedCount={selectedFields.length}
                      topOutliers={topOutliers}
                      topVariability={topVariability}
                    />

                    <Box
                      sx={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: 0.8,
                      }}
                    >
                      <Chip size="small" variant="outlined" label="Box = Q1 ~ Q3" />
                      <Chip size="small" variant="outlined" label="가운데 선 = median" />
                      <Chip size="small" variant="outlined" label="수염 = whisker 범위" />
                      <Chip size="small" variant="outlined" label="점 = outlier" />
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`계산 row ${boxplotData.sampled_row_count.toLocaleString('ko-KR')}건`}
                      />
                    </Box>

                    {groupedBoxplotFields.length > 1 && (
                      <Box
                        sx={{
                          border: '1px solid',
                          borderColor: '#d7e1ee',
                          borderRadius: 1.5,
                          backgroundColor: '#f8fbff',
                        }}
                      >
                        <Tabs
                          value={activeGroup?.key ?? false}
                          variant="scrollable"
                          allowScrollButtonsMobile
                          onChange={(_, value: BoxplotGroupKey) => setActiveGroupKey(value)}
                        >
                          {groupedBoxplotFields.map((group) => (
                            <Tab
                              key={group.key}
                              value={group.key}
                              label={`${group.label} (${group.fields.length})`}
                            />
                          ))}
                        </Tabs>
                      </Box>
                    )}

                    {activeGroup && (
                      <Typography variant="body2" color="text.secondary">
                        현재 그룹: {activeGroup.label} ({activeGroup.description})
                      </Typography>
                    )}

                    <Box
                      sx={{
                        border: '1px solid',
                        borderColor: '#d7e1ee',
                        borderRadius: 1.5,
                        backgroundColor: '#ffffff',
                        p: 1,
                        overflowX: 'auto',
                      }}
                    >
                      {activeGroup ? (
                        <BoxplotComparisonChart fields={activeGroup.fields} />
                      ) : (
                        <Alert severity="info">시각화할 필드 그룹이 없습니다.</Alert>
                      )}
                    </Box>

                    <Box
                      sx={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: 0.8,
                      }}
                    >
                      <Chip
                        size="small"
                        label={`전체 row ${boxplotData.total_row_count.toLocaleString('ko-KR')}건`}
                        variant="outlined"
                      />
                      <Chip
                        size="small"
                        label={`유효 row ${boxplotData.effective_row_count.toLocaleString('ko-KR')}건`}
                        variant="outlined"
                      />
                      <Chip
                        size="small"
                        label={`선택 그룹 수 ${groupedBoxplotFields.length.toLocaleString('ko-KR')}개`}
                        variant="outlined"
                      />
                    </Box>
                  </Stack>
                )}
            </Box>
          </Box>

          <Box
            sx={{
              mt: 2,
              border: '1px solid',
              borderColor: '#2d4f74',
              borderRadius: 1.8,
              backgroundColor: '#f7fbff',
              p: 2,
            }}
          >
            <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.8 }}>
              화면 안내
            </Typography>
            <Typography variant="body2" sx={{ mb: 0.5 }}>
              ? 필드를 데이터 유사성 그룹으로 분리하여 각 영역의 분포 문제를 파악합니다.
            </Typography>
            <Typography variant="body2" sx={{ mb: 0.5 }}>
              ? 상단 요약 카드에서 이상치 많은 필드와 IQR 큰 필드를 먼저 확인해 분석 우선순위를 잡을 수 있습니다.
            </Typography>
            <Typography variant="body2">
              ? 그룹 탭을 반복하면서 분포 및 중앙값과 이상치 개수를 비교하면 변동성 큰 센서를 빠르게 찾아낼 수 있습니다.
            </Typography>
          </Box>
        </CardContent>
      </Card>
    </Stack>
  );
}

