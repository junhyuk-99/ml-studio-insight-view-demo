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
  DataExplorationAppliedMatchFilter,
  DataExplorationDatasetOption,
  HistogramFieldOption,
  TimeseriesDataResponse,
  TimeseriesFieldData,
  TimeseriesPoint,
} from '../types/dataExploration';

const DEFAULT_MAX_POINTS = 2000;
const MAX_POINTS = 10000;
const MAX_SELECTED_FIELDS = 12;
const KST_OFFSET_HOURS = 9;

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

function formatKstDateTime(utcIsoText: string | null | undefined): string {
  if (!utcIsoText) {
    return '-';
  }
  const date = new Date(utcIsoText);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }
  return date.toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour12: false,
  });
}

function formatKstAxisLabel(utcIsoText: string): string {
  const date = new Date(utcIsoText);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }
  return date.toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
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

type PlotPoint = {
  x: number;
  y: number;
  timestamp: string;
  value: number;
};

type TimeseriesMiniChartProps = {
  points: TimeseriesPoint[];
  fieldName: string;
};

function TimeseriesMiniChart({ points, fieldName }: TimeseriesMiniChartProps) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  const sortedPoints = useMemo(
    () => points.slice().sort((left, right) => left.timestamp.localeCompare(right.timestamp)),
    [points],
  );

  if (sortedPoints.length === 0) {
    return <Alert severity="info">선택한 항목의 수치 데이터가 없습니다.</Alert>;
  }

  const width = 540;
  const height = 280;
  const margin = { top: 24, right: 16, bottom: 56, left: 72 };
  const plotWidth = width - margin.left - margin.right;
  const plotHeight = height - margin.top - margin.bottom;

  const values = sortedPoints.map((point) => point.value);
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const valueRange = maxValue - minValue > 0 ? maxValue - minValue : Math.max(Math.abs(maxValue), 1) * 0.01;

  const plottedPoints: PlotPoint[] = sortedPoints.map((point, index) => {
    const x =
      margin.left +
      (sortedPoints.length <= 1 ? plotWidth / 2 : (plotWidth * index) / (sortedPoints.length - 1));
    const y = margin.top + (1 - (point.value - minValue) / valueRange) * plotHeight;
    return {
      x,
      y,
      timestamp: point.timestamp,
      value: point.value,
    };
  });

  const linePath = plottedPoints
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
    .join(' ');
  const areaPath =
    plottedPoints.length > 1
      ? `${linePath} L ${plottedPoints[plottedPoints.length - 1].x} ${margin.top + plotHeight} L ${plottedPoints[0].x} ${margin.top + plotHeight} Z`
      : '';

  const yTickCount = 4;
  const yTicks = Array.from({ length: yTickCount + 1 }).map((_, index) => {
    const ratio = index / yTickCount;
    const value = minValue + valueRange * ratio;
    const y = margin.top + (1 - ratio) * plotHeight;
    return { value, y };
  });

  const xTickIndexes = Array.from(
    new Set([0, Math.floor(plottedPoints.length / 2), Math.max(plottedPoints.length - 1, 0)]),
  );

  const clampedHoveredIndex =
    hoveredIndex == null
      ? null
      : Math.min(Math.max(hoveredIndex, 0), Math.max(plottedPoints.length - 1, 0));
  const hoveredPoint = clampedHoveredIndex == null ? null : plottedPoints[clampedHoveredIndex];

  const handleMouseMove = (event: React.MouseEvent<SVGRectElement>) => {
    if (plottedPoints.length === 0) {
      return;
    }

    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width <= 0) {
      return;
    }

    const relativeX = event.clientX - rect.left;
    const ratio = Math.min(1, Math.max(0, relativeX / rect.width));
    const nextIndex = Math.round(ratio * (plottedPoints.length - 1));
    setHoveredIndex(nextIndex);
  };

  return (
    <Box sx={{ position: 'relative', width: '100%', overflowX: 'auto' }}>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        width="100%"
        preserveAspectRatio="xMidYMid meet"
        role="img"
        aria-label={`${fieldName} 시계열 차트`}
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

        {areaPath && <path d={areaPath} fill="rgba(37, 99, 235, 0.1)" />}
        {linePath && <path d={linePath} fill="none" stroke="#2563eb" strokeWidth={2} />}

        {plottedPoints.length <= 180 &&
          plottedPoints.map((point, index) => (
            <circle
              key={`point-${index}`}
              cx={point.x}
              cy={point.y}
              r={2}
              fill="#1d4ed8"
              opacity={0.85}
            />
          ))}

        {hoveredPoint && (
          <g>
            <line
              x1={hoveredPoint.x}
              y1={margin.top}
              x2={hoveredPoint.x}
              y2={height - margin.bottom}
              stroke="#1d4ed8"
              strokeDasharray="4 4"
              strokeWidth={1}
            />
            <circle
              cx={hoveredPoint.x}
              cy={hoveredPoint.y}
              r={4.5}
              fill="#1d4ed8"
              stroke="#ffffff"
              strokeWidth={1.8}
            />
          </g>
        )}

        {xTickIndexes.map((index) => {
          const tickPoint = plottedPoints[index];
          return (
            <g key={`x-tick-${index}`}>
              <line
                x1={tickPoint.x}
                y1={height - margin.bottom}
                x2={tickPoint.x}
                y2={height - margin.bottom + 5}
                stroke="#94a3b8"
                strokeWidth={1}
              />
              <text
                x={tickPoint.x}
                y={height - margin.bottom + 18}
                textAnchor="middle"
                fontSize={11}
                fill="#607089"
              >
                {formatKstAxisLabel(tickPoint.timestamp)}
              </text>
            </g>
          );
        })}

        <text
          x={margin.left + plotWidth / 2}
          y={height - 12}
          textAnchor="middle"
          fontSize={12}
          fontWeight={700}
          fill="#41546f"
        >
          시간
        </text>

        <text
          x={margin.left - 12}
          y={margin.top - 8}
          textAnchor="end"
          fontSize={11}
          fontWeight={700}
          fill="#41546f"
        >
          값
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

      {hoveredPoint && (
        <Box
          sx={{
            position: 'absolute',
            top: 10,
            right: 10,
            minWidth: 220,
            p: 1,
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1.5,
            backgroundColor: 'rgba(255, 255, 255, 0.96)',
            boxShadow: '0 2px 8px rgba(15, 23, 42, 0.12)',
          }}
        >
          <Typography variant="caption" display="block" color="text.secondary">
            시간(KST): {formatKstDateTime(hoveredPoint.timestamp)}
          </Typography>
          <Typography variant="caption" display="block" color="text.secondary">
            값 {formatMetric(hoveredPoint.value, 4)}
          </Typography>
        </Box>
      )}
    </Box>
  );
}

type TimeseriesCardProps = {
  data: TimeseriesFieldData;
};

function TimeseriesCard({ data }: TimeseriesCardProps) {
  return (
    <Card
      variant="outlined"
      sx={{
        borderRadius: 2,
        height: '100%',
        borderColor: '#d9e2ef',
        boxShadow: '0 1px 6px rgba(15, 23, 42, 0.04)',
      }}
    >
      <CardContent>
        <Typography
          variant="subtitle1"
          sx={{
            fontWeight: 800,
            wordBreak: 'break-all',
          }}
        >
          {data.field}
        </Typography>

        <Box
          sx={{
            mt: 0.8,
            display: 'flex',
            flexWrap: 'wrap',
            gap: 0.8,
          }}
        >
          <Chip size="small" label={`표본 ${data.sample_count.toLocaleString('ko-KR')}건`} />
          <Chip size="small" variant="outlined" label={`최소 ${formatMetric(data.min)}`} />
          <Chip size="small" variant="outlined" label={`최대 ${formatMetric(data.max)}`} />
          <Chip size="small" variant="outlined" label={`평균 ${formatMetric(data.avg)}`} />
          <Chip size="small" variant="outlined" label={`포인트 ${data.points.length.toLocaleString('ko-KR')}건`} />
        </Box>

        <Divider sx={{ my: 1.4 }} />
        <TimeseriesMiniChart points={data.points} fieldName={data.field} />
      </CardContent>
    </Card>
  );
}

export function DataExplorationTimeseriesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [initialKstMonthRange] = useState(() => getCurrentKstMonthRange());
  const [startDate, setStartDate] = useState(initialKstMonthRange.startDate);
  const [endDate, setEndDate] = useState(initialKstMonthRange.endDate);
  const [maxPoints, setMaxPoints] = useState(DEFAULT_MAX_POINTS);
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

  const [timeseriesData, setTimeseriesData] = useState<TimeseriesDataResponse | null>(null);

  const [datasetLoading, setDatasetLoading] = useState(false);
  const [fieldLoading, setFieldLoading] = useState(false);
  const [queryLoading, setQueryLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [pageInfo, setPageInfo] = useState<string | null>(null);
  const [selectionWarning, setSelectionWarning] = useState<string | null>(null);
  const initialLoadStartedRef = useRef(false);

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

  const orderedTimeseriesCards = useMemo(() => {
    if (!timeseriesData) {
      return [] as TimeseriesFieldData[];
    }

    const fieldMap = new Map(timeseriesData.field_timeseries.map((row) => [row.field, row]));
    return selectedFields
      .map((field) => fieldMap.get(field))
      .filter((row): row is TimeseriesFieldData => Boolean(row));
  }, [timeseriesData, selectedFields]);

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

  const runTimeseriesQuery = useCallback(
    async (
      datasetKey: string,
      queryStartDate: string,
      queryEndDate: string,
      queryFields: string[],
      queryEquipmentId: string,
      queryMaxPoints: number,
    ) => {
      if (!datasetKey.trim()) {
        setPageError(null);
        setPageInfo('데이터셋을 선택해 주세요.');
        setTimeseriesData(null);
        return;
      }

      const normalizedEquipmentId = queryEquipmentId.trim().toUpperCase();
      if (
        normalizedEquipmentId &&
        selectedDatasetMccode &&
        normalizedEquipmentId !== selectedDatasetMccode.toUpperCase()
      ) {
        setPageError(`선택한 dataset은 ${selectedDatasetMccode}인데 입력 MCCODE는 ${normalizedEquipmentId}입니다.`);
        setPageInfo(null);
        setTimeseriesData(null);
        return;
      }

      const dateValidationMessage = validateDateInputs(queryStartDate, queryEndDate);
      if (dateValidationMessage) {
        setPageError(dateValidationMessage);
        setPageInfo(null);
        setTimeseriesData(null);
        return;
      }

      if (queryFields.length === 0) {
        setPageError(null);
        setPageInfo('항목을 선택해 주세요.');
        setTimeseriesData(null);
        return;
      }

      const fromIso = toUtcStartIsoFromKstDate(queryStartDate);
      const toIso = toUtcEndIsoFromKstDate(queryEndDate);
      if (!fromIso || !toIso) {
        setPageError('날짜 변환에 실패했습니다. 날짜를 다시 선택해 주세요.');
        setPageInfo(null);
        setTimeseriesData(null);
        return;
      }

      setQueryLoading(true);
      setPageError(null);
      setPageInfo(null);

      try {
        const response = await dataExplorationService.getTimeseriesData({
          dataset_key: datasetKey,
          equipment_id: queryEquipmentId.trim() || undefined,
          from: fromIso,
          to: toIso,
          selected_fields: queryFields,
          max_points: queryMaxPoints,
        });

        setTimeseriesData(response);
        setAppliedMatchFilter(normalizeAppliedMatchFilter(response.applied_match_filter));

        if (response.total_row_count === 0) {
          setPageInfo('조회된 데이터가 없습니다.');
          return;
        }

        if (response.sampling_applied) {
          setPageInfo(
            `데이터가 많아 ${response.sampling_step.toLocaleString('ko-KR')}개 간격으로 샘플링하여 ${response.sampled_row_count.toLocaleString('ko-KR')}건을 표시했습니다.`,
          );
          return;
        }

        setPageInfo(null);
      } catch (error: unknown) {
        setTimeseriesData(null);
        setPageError(error instanceof Error ? error.message : '시계열 데이터 조회에 실패했습니다.');
      } finally {
        setQueryLoading(false);
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
    if (!datasetKey.trim()) {
      setFieldOptions([]);
      setSelectedFields([]);
      setDefaultSelectedFields([]);
      setAppliedMatchFilter({});
      setTimeseriesData(null);
      setPageInfo('데이터셋을 선택해 주세요.');
      return;
    }

    setFieldLoading(true);
    setPageError(null);
    setSelectionWarning(null);

    try {
      const fromIso = toUtcStartIsoFromKstDate(startDate);
      const toIso = toUtcEndIsoFromKstDate(endDate);
      const response = await dataExplorationService.getTimeseriesFields({
        datasetKey,
        from: fromIso ?? undefined,
        to: toIso ?? undefined,
        equipmentId: equipmentIdFilter.trim() || undefined,
      });

      setMinTimestamp(response.min_timestamp);
      setMaxTimestamp(response.max_timestamp);
      const normalizedFieldOptions = normalizeExplorationFieldOptions(response.fields ?? []);
      setFieldOptions(normalizedFieldOptions);
      setAppliedMatchFilter(normalizeAppliedMatchFilter(response.applied_match_filter));

      const resolvedMinDate = toKSTDateInput(response.min_timestamp);
      const resolvedMaxDate = toKSTDateInput(response.max_timestamp);
      const initialSelectedFields = buildInitialSelectedFields(
        normalizedFieldOptions,
        response.default_selected_fields,
        MAX_SELECTED_FIELDS,
      );

      setDefaultSelectedFields(initialSelectedFields);
      setSelectedFields(initialSelectedFields);
      setMaxPoints(DEFAULT_MAX_POINTS);

      if (allDataMode && resolvedMinDate && resolvedMaxDate) {
        setStartDate(resolvedMinDate);
        setEndDate(resolvedMaxDate);
      }

      if (response.row_count === 0) {
        setPageInfo('조회된 데이터가 없습니다.');
        setTimeseriesData(null);
        return;
      }

      if (initialSelectedFields.length === 0) {
        setPageInfo('항목을 선택해 주세요.');
        setTimeseriesData(null);
        return;
      }

      if (runInitialQuery) {
        const queryStartDate = allDataMode && resolvedMinDate ? resolvedMinDate : startDate;
        const queryEndDate = allDataMode && resolvedMaxDate ? resolvedMaxDate : endDate;

        await runTimeseriesQuery(
          datasetKey,
          queryStartDate,
          queryEndDate,
          initialSelectedFields,
          equipmentIdFilter,
          DEFAULT_MAX_POINTS,
        );
      } else {
        setTimeseriesData(null);
        setPageInfo('데이터셋이 변경되었습니다. 필드를 확인한 후 조회를 실행해 주세요.');
      }
    } catch (error: unknown) {
      setPageError(error instanceof Error ? error.message : '시계열 초기 정보를 불러오지 못했습니다.');
    } finally {
      setFieldLoading(false);
    }
  }, [allDataMode, endDate, equipmentIdFilter, runTimeseriesQuery, startDate]);

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
        setAppliedMatchFilter({});
        setTimeseriesData(null);
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
    void runTimeseriesQuery(selectedDatasetKey, startDate, endDate, selectedFields, equipmentIdFilter, maxPoints);
  };

  const handleReset = () => {
    const currentMonthRange = getCurrentKstMonthRange();

    setMaxPoints(DEFAULT_MAX_POINTS);
    setSelectedFields(defaultSelectedFields);
    setSelectionWarning(null);
    setAllDataMode(false);
    setActiveMonth(currentMonthRange.monthKey);
    setStartDate(currentMonthRange.startDate);
    setEndDate(currentMonthRange.endDate);

    if (defaultSelectedFields.length === 0) {
      setTimeseriesData(null);
      setPageInfo('항목을 선택해 주세요.');
      return;
    }

    void runTimeseriesQuery(
      selectedDatasetKey,
      currentMonthRange.startDate,
      currentMonthRange.endDate,
      defaultSelectedFields,
      equipmentIdFilter,
      DEFAULT_MAX_POINTS,
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
    setTimeseriesData(null);
    setAppliedMatchFilter({});
    setSelectionWarning(null);
    setPageInfo('데이터셋이 변경되었습니다. 필드를 다시 불러오는 중입니다.');
    void loadFieldOptions(normalized, false);
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
          setSelectionWarning(`최대 ${MAX_SELECTED_FIELDS}개 항목까지 선택할 수 있습니다.`);
          return previous;
        }
        return [...previous, fieldName];
      }

      return previous.filter((field) => field !== fieldName);
    });
  };

  const handleMaxPointsChange = (value: string) => {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      setMaxPoints(DEFAULT_MAX_POINTS);
      return;
    }
    const normalized = Math.min(MAX_POINTS, Math.max(100, Math.floor(parsed)));
    setMaxPoints(normalized);
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
          시계열 추이
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.7 }}>
          선택한 데이터셋의 시간 흐름에 따른 수치 변화를 월 단위 또는 전체 범위로 조회합니다.
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
                label="Max points"
                type="number"
                value={maxPoints}
                onChange={(event) => handleMaxPointsChange(event.target.value)}
                inputProps={{
                  min: 100,
                  max: MAX_POINTS,
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
                항목 선택
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.4 }}>
                수치형 컬럼을 선택하면 선택한 항목별 시계열 카드가 생성됩니다.
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
                      항목 목록을 불러오는 중입니다...
                    </Typography>
                  </Box>
                )}

                {!fieldLoading && fieldOptions.length === 0 && (
                  <Alert severity="info">선택 가능한 수치형 항목이 없습니다.</Alert>
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
                시계열 그래프 시각화
              </Typography>
              <Divider sx={{ my: 1.2 }} />

              {queryLoading && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 1 }}>
                  <CircularProgress size={20} />
                  <Typography variant="body2" color="text.secondary">
                    시계열 데이터를 계산하는 중입니다...
                  </Typography>
                </Box>
              )}

              {!queryLoading && selectedFields.length === 0 && (
                <Alert severity="info">항목을 선택해 주세요.</Alert>
              )}

              {!queryLoading &&
                selectedFields.length > 0 &&
                timeseriesData &&
                timeseriesData.total_row_count === 0 && (
                  <Alert severity="info">조회된 데이터가 없습니다.</Alert>
                )}

              {!queryLoading &&
                selectedFields.length > 0 &&
                orderedTimeseriesCards.length > 0 &&
                (!timeseriesData || timeseriesData.total_row_count > 0) && (
                  <Box
                    sx={{
                      maxHeight: {
                        xs: 'none',
                        lg: 760,
                      },
                      overflowY: {
                        xs: 'visible',
                        lg: 'auto',
                      },
                      pr: {
                        xs: 0,
                        lg: 0.5,
                      },
                    }}
                  >
                    <Box
                      sx={{
                        display: 'grid',
                        gap: 1.4,
                        gridTemplateColumns: {
                          xs: '1fr',
                          xl: 'repeat(2, minmax(0, 1fr))',
                        },
                      }}
                    >
                      {orderedTimeseriesCards.map((row) => (
                        <TimeseriesCard key={row.field} data={row} />
                      ))}
                    </Box>
                  </Box>
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
              시간에 따라 센서값이 어떻게 변하는지 보여줍니다.
            </Typography>
            <Typography variant="body2" sx={{ mb: 0.5 }}>
              설비가 정상 운전 중인지, 특정 시점에 급격히 변하는지, 드리프트가 있는지 확인할 수 있습니다.
            </Typography>
            <Typography variant="body2">
              이상 탐지 전후의 라인 그래프로 급상승, 급하강, 반복 진동, 노이즈 증가를 먼저 확인합니다.
            </Typography>
          </Box>
        </CardContent>
      </Card>
    </Stack>
  );
}

