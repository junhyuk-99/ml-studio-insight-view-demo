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
  Tooltip,
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
  CorrelationHeatmapDataResponse,
  DataExplorationAppliedMatchFilter,
  DataExplorationDatasetOption,
  HistogramFieldOption,
} from '../types/dataExploration';

const DEFAULT_MAX_ROWS = 5000;
const MAX_ROWS = 50000;
const MAX_SELECTED_FIELDS = 12;
const MIN_SELECTED_FIELDS = 2;
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
    minimumFractionDigits: digits,
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

type Rgb = {
  r: number;
  g: number;
  b: number;
};

const NEGATIVE_COLOR: Rgb = { r: 37, g: 99, b: 235 };
const NEUTRAL_COLOR: Rgb = { r: 248, g: 250, b: 252 };
const POSITIVE_COLOR: Rgb = { r: 220, g: 38, b: 38 };

function lerpColor(left: Rgb, right: Rgb, ratio: number): Rgb {
  const clamped = Math.min(1, Math.max(0, ratio));
  return {
    r: Math.round(left.r + (right.r - left.r) * clamped),
    g: Math.round(left.g + (right.g - left.g) * clamped),
    b: Math.round(left.b + (right.b - left.b) * clamped),
  };
}

function toCssRgb(color: Rgb): string {
  return `rgb(${color.r}, ${color.g}, ${color.b})`;
}

function pickTextColor(background: Rgb): string {
  const luminance = background.r * 0.299 + background.g * 0.587 + background.b * 0.114;
  return luminance > 160 ? '#1f2937' : '#f8fafc';
}

function resolveCellStyle(value: number | null): { backgroundColor: string; color: string } {
  if (value == null || Number.isNaN(value)) {
    return {
      backgroundColor: '#eef2f7',
      color: '#475569',
    };
  }

  const clamped = Math.max(-1, Math.min(1, value));
  const color =
    clamped <= 0
      ? lerpColor(NEGATIVE_COLOR, NEUTRAL_COLOR, clamped + 1)
      : lerpColor(NEUTRAL_COLOR, POSITIVE_COLOR, clamped);

  return {
    backgroundColor: toCssRgb(color),
    color: pickTextColor(color),
  };
}

type CorrelationCellTooltipProps = {
  xField: string;
  yField: string;
  correlation: number | null;
  sampleCount: number;
};

function CorrelationCellTooltip({
  xField,
  yField,
  correlation,
  sampleCount,
}: CorrelationCellTooltipProps) {
  return (
    <Box>
      <Typography variant="caption" sx={{ display: 'block' }}>
        X: {xField}
      </Typography>
      <Typography variant="caption" sx={{ display: 'block' }}>
        Y: {yField}
      </Typography>
      <Typography variant="caption" sx={{ display: 'block' }}>
        corr: {correlation == null ? '-' : formatMetric(correlation, 3)}
      </Typography>
      <Typography variant="caption" sx={{ display: 'block' }}>
        sample: {sampleCount.toLocaleString('ko-KR')}건
      </Typography>
      {correlation == null && (
        <Typography variant="caption" sx={{ display: 'block', mt: 0.4, opacity: 0.9 }}>
          유효 표본 부족 또는 분산 0으로 계산 불가
        </Typography>
      )}
    </Box>
  );
}

type CorrelationHeatmapTableProps = {
  data: CorrelationHeatmapDataResponse;
};

function CorrelationHeatmapTable({ data }: CorrelationHeatmapTableProps) {
  const fields = data.fields ?? [];
  const matrix = data.matrix ?? [];
  const pairSampleCounts = data.pair_sample_counts ?? [];

  if (fields.length === 0) {
    return <Alert severity="info">표시할 필드가 없습니다.</Alert>;
  }

  return (
    <Box
      sx={{
        border: '1px solid',
        borderColor: '#d7e1ee',
        borderRadius: 1.5,
        overflow: 'auto',
        backgroundColor: '#ffffff',
        maxHeight: {
          xs: 520,
          lg: 760,
        },
      }}
    >
      <Box
        component="table"
        sx={{
          borderCollapse: 'separate',
          borderSpacing: 0,
          minWidth: '100%',
          width: 'max-content',
        }}
      >
        <Box component="thead">
          <Box component="tr">
            <Box
              component="th"
              sx={{
                position: 'sticky',
                top: 0,
                left: 0,
                zIndex: 5,
                backgroundColor: '#f1f5f9',
                borderBottom: '1px solid #d7e1ee',
                borderRight: '1px solid #d7e1ee',
                px: 1.2,
                py: 1,
                minWidth: 160,
                textAlign: 'left',
                fontSize: 12,
                fontWeight: 800,
                color: '#1e293b',
              }}
            >
              Field
            </Box>
            {fields.map((field) => (
              <Box
                key={`header-${field}`}
                component="th"
                sx={{
                  position: 'sticky',
                  top: 0,
                  zIndex: 4,
                  backgroundColor: '#f8fafc',
                  borderBottom: '1px solid #d7e1ee',
                  borderRight: '1px solid #e3ebf4',
                  px: 1,
                  py: 0.9,
                  minWidth: 96,
                  maxWidth: 96,
                  textAlign: 'center',
                  color: '#334155',
                }}
              >
                <Typography
                  variant="caption"
                  sx={{
                    display: 'inline-block',
                    lineHeight: 1.25,
                    whiteSpace: 'normal',
                    wordBreak: 'break-word',
                    fontWeight: 700,
                  }}
                >
                  {field}
                </Typography>
              </Box>
            ))}
          </Box>
        </Box>

        <Box component="tbody">
          {fields.map((rowField, rowIndex) => (
            <Box component="tr" key={`row-${rowField}`}>
              <Box
                component="th"
                sx={{
                  position: 'sticky',
                  left: 0,
                  zIndex: 3,
                  backgroundColor: '#f8fafc',
                  borderBottom: '1px solid #e3ebf4',
                  borderRight: '1px solid #d7e1ee',
                  px: 1.2,
                  py: 1,
                  minWidth: 160,
                  maxWidth: 160,
                  textAlign: 'left',
                }}
              >
                <Typography
                  variant="body2"
                  sx={{
                    fontWeight: 700,
                    lineHeight: 1.3,
                    wordBreak: 'break-all',
                    color: '#1f2937',
                  }}
                >
                  {rowField}
                </Typography>
              </Box>

              {fields.map((columnField, columnIndex) => {
                const correlation = matrix[rowIndex]?.[columnIndex] ?? null;
                const sampleCount = pairSampleCounts[rowIndex]?.[columnIndex] ?? 0;
                const style = resolveCellStyle(correlation);
                const isDiagonal = rowIndex === columnIndex;

                return (
                  <Tooltip
                    key={`cell-${rowField}-${columnField}`}
                    placement="top"
                    arrow
                    title={
                      <CorrelationCellTooltip
                        xField={columnField}
                        yField={rowField}
                        correlation={correlation}
                        sampleCount={sampleCount}
                      />
                    }
                  >
                    <Box
                      component="td"
                      sx={{
                        px: 1,
                        py: 0.8,
                        minWidth: 96,
                        maxWidth: 96,
                        textAlign: 'center',
                        borderBottom: '1px solid #e3ebf4',
                        borderRight: '1px solid #e3ebf4',
                        fontSize: 12,
                        fontWeight: isDiagonal ? 800 : 700,
                        userSelect: 'none',
                        ...style,
                      }}
                    >
                      {correlation == null ? '-' : formatMetric(correlation, 3)}
                    </Box>
                  </Tooltip>
                );
              })}
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
}

export function DataExplorationCorrelationHeatmapPage() {
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

  const [heatmapData, setHeatmapData] = useState<CorrelationHeatmapDataResponse | null>(null);

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

  const runCorrelationQuery = useCallback(
    async (
      datasetKey: string,
      queryStartDate: string,
      queryEndDate: string,
      queryFields: string[],
      queryEquipmentId: string,
      queryMaxRows: number,
    ) => {
      if (!datasetKey.trim()) {
        setPageError(null);
        setPageInfo('데이터셋을 선택해 주세요.');
        setHeatmapData(null);
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
        setHeatmapData(null);
        return;
      }

      const dateValidationMessage = validateDateInputs(queryStartDate, queryEndDate);
      if (dateValidationMessage) {
        setPageError(dateValidationMessage);
        setPageInfo(null);
        setHeatmapData(null);
        return;
      }

      if (queryFields.length < MIN_SELECTED_FIELDS) {
        setPageError(null);
        setPageInfo('필드를 2개 이상 선택해 주세요.');
        setHeatmapData(null);
        return;
      }

      const fromIso = toUtcStartIsoFromKstDate(queryStartDate);
      const toIso = toUtcEndIsoFromKstDate(queryEndDate);
      if (!fromIso || !toIso) {
        setPageError('날짜 변환에 실패했습니다. 날짜를 다시 선택해 주세요.');
        setPageInfo(null);
        setHeatmapData(null);
        return;
      }

      const requestId = ++queryRequestIdRef.current;

      setQueryLoading(true);
      setPageError(null);
      setPageInfo(null);

      try {
        const response = await dataExplorationService.getCorrelationHeatmapData({
          dataset_key: datasetKey,
          equipment_id: queryEquipmentId.trim() || undefined,
          from: fromIso,
          to: toIso,
          selected_fields: queryFields,
          max_rows: queryMaxRows,
        });

        if (requestId !== queryRequestIdRef.current) {
          return;
        }

        setHeatmapData(response);
        setAppliedMatchFilter(normalizeAppliedMatchFilter(response.applied_match_filter));

        if (response.total_row_count === 0) {
          setPageInfo('조회된 데이터가 없습니다.');
          return;
        }

        if (response.sampling_applied) {
          setPageInfo(
            `데이터가 많아 ${response.sampling_step.toLocaleString('ko-KR')}개 간격으로 샘플링하여 ${response.sampled_row_count.toLocaleString('ko-KR')}건을 계산했습니다.`,
          );
          return;
        }

        setPageInfo(null);
      } catch (error: unknown) {
        if (requestId !== queryRequestIdRef.current) {
          return;
        }

        setHeatmapData(null);
        setPageError(error instanceof Error ? error.message : '상관관계 히트맵 조회에 실패했습니다.');
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
      setAppliedMatchFilter({});
      setHeatmapData(null);
      setPageInfo('데이터셋을 선택해 주세요.');
      return;
    }

    setFieldLoading(true);
    setPageError(null);
    setSelectionWarning(null);

    try {
      const fromIso = toUtcStartIsoFromKstDate(startDate);
      const toIso = toUtcEndIsoFromKstDate(endDate);
      const response = await dataExplorationService.getCorrelationHeatmapFields({
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
        setHeatmapData(null);
        return;
      }

      if (initialSelectedFields.length < MIN_SELECTED_FIELDS) {
        setPageInfo('필드를 2개 이상 선택해 주세요.');
        setHeatmapData(null);
        return;
      }

      if (runInitialQuery) {
        const queryStartDate = allDataMode && resolvedMinDate ? resolvedMinDate : startDate;
        const queryEndDate = allDataMode && resolvedMaxDate ? resolvedMaxDate : endDate;

        await runCorrelationQuery(
          datasetKey,
          queryStartDate,
          queryEndDate,
          initialSelectedFields,
          equipmentIdFilter,
          DEFAULT_MAX_ROWS,
        );
      } else {
        setHeatmapData(null);
        setPageInfo('데이터셋이 변경되었습니다. 필드를 확인한 뒤 조회를 실행해 주세요.');
      }
    } catch (error: unknown) {
      if (requestId !== fieldRequestIdRef.current) {
        return;
      }

      setPageError(
        error instanceof Error ? error.message : '상관관계 히트맵 초기 정보를 불러오지 못했습니다.',
      );
    } finally {
      if (requestId === fieldRequestIdRef.current) {
        setFieldLoading(false);
      }
    }
  }, [allDataMode, endDate, equipmentIdFilter, runCorrelationQuery, startDate]);

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
        setHeatmapData(null);
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
    void runCorrelationQuery(selectedDatasetKey, startDate, endDate, selectedFields, equipmentIdFilter, maxRows);
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

    if (defaultSelectedFields.length < MIN_SELECTED_FIELDS) {
      setHeatmapData(null);
      setPageInfo('필드를 2개 이상 선택해 주세요.');
      return;
    }

    void runCorrelationQuery(
      selectedDatasetKey,
      currentMonthRange.startDate,
      currentMonthRange.endDate,
      defaultSelectedFields,
      equipmentIdFilter,
      DEFAULT_MAX_ROWS,
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
    setHeatmapData(null);
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
          상관관계 히트맵
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.7 }}>
          선택한 데이터셋의 수치형 변수 간 Pearson 상관관계를 월 단위 또는 전체 범위로 조회합니다.
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
                수치형 컬럼을 선택하면 선택한 필드 간 상관관계 히트맵을 계산합니다.
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
                상관관계 히트맵 시각화
              </Typography>
              <Divider sx={{ my: 1.2 }} />

              {queryLoading && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 1 }}>
                  <CircularProgress size={20} />
                  <Typography variant="body2" color="text.secondary">
                    상관관계를 계산하는 중입니다...
                  </Typography>
                </Box>
              )}

              {!queryLoading && selectedFields.length < MIN_SELECTED_FIELDS && (
                <Alert severity="info">필드를 2개 이상 선택해 주세요.</Alert>
              )}

              {!queryLoading &&
                selectedFields.length >= MIN_SELECTED_FIELDS &&
                heatmapData &&
                heatmapData.total_row_count === 0 && (
                  <Alert severity="info">조회된 데이터가 없습니다.</Alert>
                )}

              {!queryLoading &&
                selectedFields.length >= MIN_SELECTED_FIELDS &&
                heatmapData &&
                heatmapData.total_row_count > 0 && (
                  <Stack spacing={1.2}>
                    <Box
                      sx={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: 0.8,
                      }}
                    >
                      <Chip
                        size="small"
                        label={`선택 필드 ${heatmapData.fields.length.toLocaleString('ko-KR')}개`}
                      />
                      <Chip size="small" variant="outlined" label={`계산 방식 ${heatmapData.method}`} />
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`전체 row ${heatmapData.total_row_count.toLocaleString('ko-KR')}건`}
                      />
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`유효 row ${heatmapData.effective_row_count.toLocaleString('ko-KR')}건`}
                      />
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`계산 row ${heatmapData.sampled_row_count.toLocaleString('ko-KR')}건`}
                      />
                    </Box>

                    <Box
                      sx={{
                        border: '1px solid',
                        borderColor: '#d7e1ee',
                        borderRadius: 1.5,
                        backgroundColor: '#f8fbff',
                        p: 1.2,
                      }}
                    >
                      <Typography variant="caption" sx={{ display: 'block', color: '#334155', mb: 0.6 }}>
                        색상 기준: 음의 상관(-1) - 무관(0) - 양의 상관(+1)
                      </Typography>
                      <Box
                        sx={{
                          height: 12,
                          borderRadius: 999,
                          border: '1px solid #c8d4e3',
                          background:
                            'linear-gradient(90deg, rgb(37,99,235) 0%, rgb(248,250,252) 50%, rgb(220,38,38) 100%)',
                        }}
                      />
                    </Box>

                    <CorrelationHeatmapTable data={heatmapData} />
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
              변수끼리 얼마나 같이 움직이는지 한눈에 보여줍니다.
            </Typography>
            <Typography variant="body2" sx={{ mb: 0.5 }}>
              압력과 속도, 온도와 시간처럼 서로 연결된 변수 조합을 빠르게 찾을 수 있습니다.
            </Typography>
            <Typography variant="body2">
              상관성이 매우 큰 변수는 중복 정보로 참고하고, 함께 변하는 조합은 이상 징후 해석의 단서로 활용할 수 있습니다.
            </Typography>
          </Box>
        </CardContent>
      </Card>
    </Stack>
  );
}

