package com.demo.insight.dataexploration.service;

import com.demo.insight.common.schema.DynamicSchemaResolver;
import com.demo.insight.dataexploration.dto.BoxplotDataRequestDto;
import com.demo.insight.dataexploration.dto.BoxplotDataResponseDto;
import com.demo.insight.dataexploration.dto.BoxplotFieldDataDto;
import com.demo.insight.dataexploration.dto.CorrelationHeatmapDataRequestDto;
import com.demo.insight.dataexploration.dto.CorrelationHeatmapDataResponseDto;
import com.demo.insight.dataexploration.dto.DataExplorationCurrentRangeDto;
import com.demo.insight.dataexploration.dto.DataExplorationDatasetOptionDto;
import com.demo.insight.dataexploration.dto.HistogramBinDto;
import com.demo.insight.dataexploration.dto.HistogramDataRequestDto;
import com.demo.insight.dataexploration.dto.HistogramDataResponseDto;
import com.demo.insight.dataexploration.dto.HistogramFieldDataDto;
import com.demo.insight.dataexploration.dto.HistogramFieldListResponseDto;
import com.demo.insight.dataexploration.dto.HistogramFieldOptionDto;
import com.demo.insight.dataexploration.dto.TimeseriesDataRequestDto;
import com.demo.insight.dataexploration.dto.TimeseriesDataResponseDto;
import com.demo.insight.dataexploration.dto.TimeseriesFieldDataDto;
import com.demo.insight.dataexploration.dto.TimeseriesPointDto;
import com.demo.insight.dataexploration.repository.DataExplorationRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DataExplorationServiceImpl implements DataExplorationService {

    private static final Logger log = LoggerFactory.getLogger(DataExplorationServiceImpl.class);

    private static final String BOXPLOT_INVALID_REQUEST_MESSAGE =
            "Invalid boxplot query options. Please check dataset_key, selected_fields, and group_by.";
    private static final String BOX_GROUP_NULL_LABEL = "(null)";
    private static final int DEFAULT_BINS = 30;
    private static final int MAX_BINS = 100;
    private static final int MAX_SELECTED_FIELDS = 12;
    private static final int DEFAULT_TIMESERIES_POINTS = 2000;
    private static final int MAX_TIMESERIES_POINTS = 10000;
    private static final int DEFAULT_CORRELATION_MAX_ROWS = 5000;
    private static final int MAX_CORRELATION_MAX_ROWS = 50000;
    private static final int DEFAULT_BOXPLOT_MAX_ROWS = 5000;
    private static final int MAX_BOXPLOT_MAX_ROWS = 50000;
    private static final int MAX_BOXPLOT_GROUPS = 20;
    private static final int DEFAULT_BOXPLOT_OUTLIER_LIMIT = 200;
    private static final int MAX_BOXPLOT_OUTLIER_LIMIT = 1000;
    private static final int DEFAULT_FIELD_SAMPLE_LIMIT = 1000;
    private static final Duration DEFAULT_LOOKBACK_DURATION = Duration.ofDays(30);
    private static final double EPSILON = 1e-9;
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final String TIMESTAMP_FIELD = "PRDTIME";
    private static final String EQUIPMENT_FIELD = "MCCODE";
    private static final String PV_SUFFIX = "_PV";
    private static final String SV_SUFFIX = "_SV";

    private static final Set<String> META_EXCLUDED_FIELDS = Set.of(
            "_id",
            "PRDTIME",
            "timestamp",
            "REGDATE",
            "LASTDATE",
            "SYNC_DATE",
            "MCCODE",
            "equipment_id",
            "sensor_id",
            "WORKORDER",
            "PRDDATE",
            "SOURCE_DB",
            "SOURCE_TABLE",
            "IFFLAG",
            "USEFLAG",
            "REGEMP",
            "LASTEMP",
            "REMARK1",
            "REMARK2",
            "RECENT_FLAG",
            "SOURCE_TYPE_CODE",
            "SOURCE_DTL_CODE",
            "SOURCE_FILE",
            "reg_date",
            "REG_DATE"
    );
    private static final Set<String> SUPERVISED_EXCLUDED_FIELDS = Set.of(
            "label",
            "label_name",
            "label_source",
            "label_version",
            "label_reason",
            "label_reg_date",
            "source_id",
            "regime",
            "cycle_gap"
    );
    private static final Set<String> STATUS_CATEGORY_FIELDS = Set.of(
            "ANAL_STAT",
            "OPSTAT",
            "HEATER_STAT",
            "OPALARM",
            "SEGMENT_NO",
            "SEGMENT_TIME",
            "SEGMENT_TOTAL",
            "PAT_PGM"
    );

    private static final Set<String> SEQUENCE_CANDIDATE_FIELDS = Set.of();
    private static final List<String> DEFAULT_BOXPLOT_GROUP_BY_FIELDS = List.of(
            "MCCODE",
            "equipment_id",
            "SOURCE_TYPE_CODE",
            "SOURCE_DTL_CODE"
    );
    private static final List<String> LABELED_BOXPLOT_GROUP_BY_FIELDS = List.of(
            "label",
            "label_name",
            "MCCODE",
            "equipment_id",
            "SOURCE_TYPE_CODE",
            "SOURCE_DTL_CODE"
    );
    private static final Set<String> META_EXCLUDED_FIELDS_LOWER = META_EXCLUDED_FIELDS.stream()
            .map(field -> field.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> SUPERVISED_EXCLUDED_FIELDS_LOWER = SUPERVISED_EXCLUDED_FIELDS.stream()
            .map(field -> field.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> STATUS_CATEGORY_FIELDS_LOWER = STATUS_CATEGORY_FIELDS.stream()
            .map(field -> field.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> SEQUENCE_CANDIDATE_FIELDS_LOWER = SEQUENCE_CANDIDATE_FIELDS.stream()
            .map(field -> field.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    private final DataExplorationRepository dataExplorationRepository;
    private final DynamicSchemaResolver schemaResolver;
    private final DataExplorationDatasetResolver datasetResolver;

    public DataExplorationServiceImpl(
            DataExplorationRepository dataExplorationRepository,
            DynamicSchemaResolver schemaResolver,
            DataExplorationDatasetResolver datasetResolver
    ) {
        this.dataExplorationRepository = dataExplorationRepository;
        this.schemaResolver = schemaResolver;
        this.datasetResolver = datasetResolver;
    }

    @Override
    public List<DataExplorationDatasetOptionDto> getDatasets() {
        return datasetResolver.getActiveDatasets();
    }

    @Override
    public HistogramFieldListResponseDto getHistogramFields(
            String datasetKey,
            String from,
            String to,
            String equipmentId
    ) {
        DataExplorationDatasetResolver.DatasetContext datasetContext = datasetResolver.resolveDatasetContext(datasetKey);
        DatasetQueryScope queryScope = resolveDatasetQueryScope(datasetContext, equipmentId);
        return buildFieldListResponse(datasetContext, queryScope, from, to, false);
    }

    @Override
    public HistogramFieldListResponseDto getTimeseriesFields(
            String datasetKey,
            String from,
            String to,
            String equipmentId
    ) {
        DataExplorationDatasetResolver.DatasetContext datasetContext = datasetResolver.resolveDatasetContext(datasetKey);
        DatasetQueryScope queryScope = resolveDatasetQueryScope(datasetContext, equipmentId);
        return buildFieldListResponse(datasetContext, queryScope, from, to, false);
    }

    @Override
    public HistogramFieldListResponseDto getCorrelationHeatmapFields(
            String datasetKey,
            String from,
            String to,
            String equipmentId
    ) {
        DataExplorationDatasetResolver.DatasetContext datasetContext = datasetResolver.resolveDatasetContext(datasetKey);
        DatasetQueryScope queryScope = resolveDatasetQueryScope(datasetContext, equipmentId);
        return buildFieldListResponse(datasetContext, queryScope, from, to, false);
    }

    @Override
    public HistogramFieldListResponseDto getBoxplotFields(
            String datasetKey,
            String from,
            String to,
            String equipmentId
    ) {
        DataExplorationDatasetResolver.DatasetContext datasetContext = datasetResolver.resolveDatasetContext(datasetKey);
        DatasetQueryScope queryScope = resolveDatasetQueryScope(datasetContext, equipmentId);
        return buildFieldListResponse(datasetContext, queryScope, from, to, true);
    }

    @Override
    public HistogramDataResponseDto getHistogramData(HistogramDataRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        DataExplorationDatasetResolver.DatasetContext datasetContext =
                datasetResolver.resolveDatasetContext(request.datasetKey());
        DatasetQueryScope queryScope = resolveDatasetQueryScope(datasetContext, request.equipmentId());

        Instant from = parseRequiredInstant(request.from(), "from");
        Instant to = parseRequiredInstant(request.to(), "to");
        validateDateRange(from, to);

        List<String> selectedFields = normalizeSelectedFields(request.selectedFields(), datasetContext);
        if (selectedFields.size() > MAX_SELECTED_FIELDS) {
            throw new IllegalArgumentException("selected_fields can contain up to " + MAX_SELECTED_FIELDS + " fields.");
        }

        int bins = resolveBins(request.bins());
        Date fromDate = Date.from(from);
        Date toDate = Date.from(to);

        long totalRowCount = dataExplorationRepository.countRowsByTimestampRange(
                datasetContext.sourceCollection(),
                fromDate,
                toDate,
                queryScope.appliedMatchFilter()
        );

        List<HistogramFieldDataDto> fieldHistograms = new ArrayList<>(selectedFields.size());
        for (String field : selectedFields) {
            DataExplorationRepository.HistogramFieldStats stats = dataExplorationRepository.aggregateFieldStats(
                    datasetContext.sourceCollection(),
                    fromDate,
                    toDate,
                    field,
                    queryScope.appliedMatchFilter()
            );

            if (stats.sampleCount() <= 0 || stats.min() == null || stats.max() == null) {
                fieldHistograms.add(new HistogramFieldDataDto(
                        field,
                        0L,
                        null,
                        null,
                        null,
                        List.of()
                ));
                continue;
            }

            List<Double> boundaries = buildBoundaries(stats.min(), stats.max(), bins);
            List<DataExplorationRepository.HistogramBucketCount> bucketCounts = dataExplorationRepository.aggregateBucketCounts(
                    datasetContext.sourceCollection(),
                    fromDate,
                    toDate,
                    field,
                    boundaries,
                    queryScope.appliedMatchFilter()
            );

            long[] counts = new long[bins];
            for (DataExplorationRepository.HistogramBucketCount bucketCount : bucketCounts) {
                int index = resolveBucketIndex(boundaries, bucketCount.bucketStart());
                if (index >= 0 && index < counts.length) {
                    counts[index] += bucketCount.count();
                }
            }

            List<HistogramBinDto> histogramBins = new ArrayList<>(bins);
            for (int index = 0; index < bins; index++) {
                histogramBins.add(new HistogramBinDto(
                        index,
                        boundaries.get(index),
                        boundaries.get(index + 1),
                        counts[index]
                ));
            }

            fieldHistograms.add(new HistogramFieldDataDto(
                    field,
                    stats.sampleCount(),
                    stats.min(),
                    stats.max(),
                    stats.avg(),
                    histogramBins
            ));
        }

        return new HistogramDataResponseDto(
                datasetContext.datasetKey(),
                datasetContext.datasetName(),
                datasetContext.displayName(),
                datasetContext.sourceCollection(),
                queryScope.appliedMatchFilterAsMap(),
                new DataExplorationCurrentRangeDto(from.toString(), to.toString()),
                from.toString(),
                to.toString(),
                bins,
                totalRowCount,
                fieldHistograms
        );
    }

    @Override
    public TimeseriesDataResponseDto getTimeseriesData(TimeseriesDataRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        DataExplorationDatasetResolver.DatasetContext datasetContext =
                datasetResolver.resolveDatasetContext(request.datasetKey());
        DatasetQueryScope queryScope = resolveDatasetQueryScope(datasetContext, request.equipmentId());

        Instant from = parseRequiredInstant(request.from(), "from");
        Instant to = parseRequiredInstant(request.to(), "to");
        validateDateRange(from, to);

        List<String> selectedFields = normalizeSelectedFields(request.selectedFields(), datasetContext);
        if (selectedFields.size() > MAX_SELECTED_FIELDS) {
            throw new IllegalArgumentException("selected_fields can contain up to " + MAX_SELECTED_FIELDS + " fields.");
        }

        int maxPoints = resolveTimeseriesMaxPoints(request.maxPoints());
        Date fromDate = Date.from(from);
        Date toDate = Date.from(to);

        long totalRowCount = dataExplorationRepository.countRowsByTimestampRange(
                datasetContext.sourceCollection(),
                fromDate,
                toDate,
                queryScope.appliedMatchFilter()
        );
        if (totalRowCount == 0L) {
            List<TimeseriesFieldDataDto> emptySeries = selectedFields.stream()
                    .map(field -> new TimeseriesFieldDataDto(field, 0L, null, null, null, List.of()))
                    .toList();
            return new TimeseriesDataResponseDto(
                    datasetContext.datasetKey(),
                    datasetContext.datasetName(),
                    datasetContext.displayName(),
                    datasetContext.sourceCollection(),
                    queryScope.appliedMatchFilterAsMap(),
                    new DataExplorationCurrentRangeDto(from.toString(), to.toString()),
                    from.toString(),
                    to.toString(),
                    maxPoints,
                    0L,
                    0L,
                    false,
                    1L,
                    emptySeries
            );
        }

        long samplingStep = resolveSamplingStep(totalRowCount, maxPoints);
        List<Document> sampledRows = dataExplorationRepository.findSampledRowsByTimestampRange(
                datasetContext.sourceCollection(),
                fromDate,
                toDate,
                selectedFields,
                samplingStep,
                maxPoints,
                null,
                queryScope.appliedMatchFilter()
        );

        Map<String, List<TimeseriesPointDto>> pointsByField = new LinkedHashMap<>();
        for (String field : selectedFields) {
            pointsByField.put(field, new ArrayList<>());
        }

        for (Document sampledRow : sampledRows) {
            String timestampText = asIsoTimestamp(sampledRow.get(TIMESTAMP_FIELD));
            if (timestampText == null) {
                continue;
            }

            for (String field : selectedFields) {
                Double value = asDouble(sampledRow.get(field));
                if (value == null || !Double.isFinite(value)) {
                    continue;
                }
                List<TimeseriesPointDto> points = pointsByField.get(field);
                if (points != null) {
                    points.add(new TimeseriesPointDto(timestampText, value));
                }
            }
        }

        List<TimeseriesFieldDataDto> fieldTimeseries = new ArrayList<>(selectedFields.size());
        for (String field : selectedFields) {
            DataExplorationRepository.HistogramFieldStats stats = dataExplorationRepository.aggregateFieldStats(
                    datasetContext.sourceCollection(),
                    fromDate,
                    toDate,
                    field,
                    queryScope.appliedMatchFilter()
            );

            List<TimeseriesPointDto> points = pointsByField.get(field);
            List<TimeseriesPointDto> immutablePoints = points == null ? List.of() : List.copyOf(points);
            fieldTimeseries.add(new TimeseriesFieldDataDto(
                    field,
                    stats.sampleCount(),
                    stats.min(),
                    stats.max(),
                    stats.avg(),
                    immutablePoints
            ));
        }

        return new TimeseriesDataResponseDto(
                datasetContext.datasetKey(),
                datasetContext.datasetName(),
                datasetContext.displayName(),
                datasetContext.sourceCollection(),
                queryScope.appliedMatchFilterAsMap(),
                new DataExplorationCurrentRangeDto(from.toString(), to.toString()),
                from.toString(),
                to.toString(),
                maxPoints,
                totalRowCount,
                sampledRows.size(),
                samplingStep > 1L,
                samplingStep,
                fieldTimeseries
        );
    }

    @Override
    public CorrelationHeatmapDataResponseDto getCorrelationHeatmapData(CorrelationHeatmapDataRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        DataExplorationDatasetResolver.DatasetContext datasetContext =
                datasetResolver.resolveDatasetContext(request.datasetKey());
        DatasetQueryScope queryScope = resolveDatasetQueryScope(datasetContext, request.equipmentId());

        Instant from = parseRequiredInstant(request.from(), "from");
        Instant to = parseRequiredInstant(request.to(), "to");
        validateDateRange(from, to);

        List<String> selectedFields = normalizeSelectedFields(request.selectedFields(), datasetContext);
        if (selectedFields.size() < 2) {
            throw new IllegalArgumentException("selected_fields must contain at least 2 fields.");
        }
        if (selectedFields.size() > MAX_SELECTED_FIELDS) {
            throw new IllegalArgumentException("selected_fields can contain up to " + MAX_SELECTED_FIELDS + " fields.");
        }

        int maxRows = resolveCorrelationMaxRows(request.maxRows());
        Date fromDate = Date.from(from);
        Date toDate = Date.from(to);

        long totalRowCount = dataExplorationRepository.countRowsByTimestampRange(
                datasetContext.sourceCollection(),
                fromDate,
                toDate,
                queryScope.appliedMatchFilter()
        );
        if (totalRowCount == 0L) {
            CorrelationComputation emptyComputation = buildCorrelationMatrix(selectedFields, List.of());
            return new CorrelationHeatmapDataResponseDto(
                    datasetContext.datasetKey(),
                    datasetContext.datasetName(),
                    datasetContext.displayName(),
                    datasetContext.sourceCollection(),
                    queryScope.appliedMatchFilterAsMap(),
                    new DataExplorationCurrentRangeDto(from.toString(), to.toString()),
                    from.toString(),
                    to.toString(),
                    "pearson",
                    maxRows,
                    0L,
                    emptyComputation.effectiveRowCount(),
                    0L,
                    false,
                    1L,
                    selectedFields,
                    emptyComputation.matrix(),
                    emptyComputation.pairSampleCounts()
            );
        }

        long samplingStep = resolveSamplingStep(totalRowCount, maxRows);
        List<Document> sampledRows = dataExplorationRepository.findSampledRowsByTimestampRange(
                datasetContext.sourceCollection(),
                fromDate,
                toDate,
                selectedFields,
                samplingStep,
                maxRows,
                null,
                queryScope.appliedMatchFilter()
        );

        CorrelationComputation computation = buildCorrelationMatrix(selectedFields, sampledRows);

        return new CorrelationHeatmapDataResponseDto(
                datasetContext.datasetKey(),
                datasetContext.datasetName(),
                datasetContext.displayName(),
                datasetContext.sourceCollection(),
                queryScope.appliedMatchFilterAsMap(),
                new DataExplorationCurrentRangeDto(from.toString(), to.toString()),
                from.toString(),
                to.toString(),
                "pearson",
                maxRows,
                totalRowCount,
                computation.effectiveRowCount(),
                sampledRows.size(),
                samplingStep > 1L,
                samplingStep,
                selectedFields,
                computation.matrix(),
                computation.pairSampleCounts()
        );
    }

    @Override
    public BoxplotDataResponseDto getBoxplotData(BoxplotDataRequestDto request) {
        try {
            if (request == null) {
                throw invalidBoxplotRequest("Request body is required.");
            }

            DataExplorationDatasetResolver.DatasetContext datasetContext =
                    resolveBoxplotDatasetContext(request.datasetKey());
            DatasetQueryScope queryScope = resolveDatasetQueryScope(datasetContext, request.equipmentId());

            Instant from = parseRequiredInstant(request.from(), "from");
            Instant to = parseRequiredInstant(request.to(), "to");
            validateDateRange(from, to);

            if (request.selectedFields() == null || request.selectedFields().isEmpty()) {
                throw invalidBoxplotRequest("selected_fields must contain at least one field.");
            }
            List<String> selectedFields = normalizeSelectedFields(request.selectedFields(), datasetContext);
            if (selectedFields.size() > MAX_SELECTED_FIELDS) {
                throw new IllegalArgumentException("selected_fields can contain up to " + MAX_SELECTED_FIELDS + " fields.");
            }

            int maxRows = resolveBoxplotMaxRows(request.maxRows());
            int outlierLimit = resolveOutlierLimit(request.maxOutliersPerField());
            Date fromDate = Date.from(from);
            Date toDate = Date.from(to);

            long totalRowCount = dataExplorationRepository.countRowsByTimestampRange(
                    datasetContext.sourceCollection(),
                    fromDate,
                    toDate,
                    queryScope.appliedMatchFilter()
            );
            if (totalRowCount == 0L) {
                List<BoxplotFieldDataDto> emptyFields = selectedFields.stream()
                        .map(field -> new BoxplotFieldDataDto(
                                field,
                                0L,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                0L,
                                List.of()
                        ))
                        .toList();
                return new BoxplotDataResponseDto(
                        datasetContext.datasetKey(),
                        datasetContext.datasetName(),
                        datasetContext.displayName(),
                        datasetContext.sourceCollection(),
                        queryScope.appliedMatchFilterAsMap(),
                        new DataExplorationCurrentRangeDto(from.toString(), to.toString()),
                        from.toString(),
                        to.toString(),
                        maxRows,
                        0L,
                        0L,
                        0L,
                        false,
                        1L,
                        emptyFields
                );
            }

            List<Document> fieldDiscoveryRows = dataExplorationRepository.findSampleRowsByTimestampRange(
                    datasetContext.sourceCollection(),
                    fromDate,
                    toDate,
                    DEFAULT_FIELD_SAMPLE_LIMIT,
                    queryScope.appliedMatchFilter()
            );
            List<String> availableColumns = schemaResolver.mergeConfiguredAndDiscoveredColumns(
                    datasetContext.sourceCollection(),
                    fieldDiscoveryRows
            );
            validateSelectedFieldsForBoxplot(selectedFields, availableColumns);

            List<String> groupByCandidates = resolveBoxplotGroupByFields(datasetContext, availableColumns);
            String resolvedGroupBy = resolveGroupByField(request.groupBy(), groupByCandidates);

            long samplingStep = resolveSamplingStep(totalRowCount, maxRows);
            List<Document> sampledRows = dataExplorationRepository.findSampledRowsByTimestampRange(
                    datasetContext.sourceCollection(),
                    fromDate,
                    toDate,
                    selectedFields,
                    samplingStep,
                    maxRows,
                    resolvedGroupBy,
                    queryScope.appliedMatchFilter()
            );

            Map<String, List<Double>> valuesByField = new LinkedHashMap<>();
            Map<String, LinkedHashMap<String, List<Double>>> groupedValuesByField = new LinkedHashMap<>();
            for (String field : selectedFields) {
                valuesByField.put(field, new ArrayList<>());
                groupedValuesByField.put(field, new LinkedHashMap<>());
            }

            long effectiveRowCount = 0L;
            for (Document sampledRow : sampledRows) {
                boolean hasValidValue = false;
                String groupValue = resolvedGroupBy == null
                        ? null
                        : resolveGroupValueLabel(sampledRow.get(resolvedGroupBy));

                for (String field : selectedFields) {
                    Double value = asDouble(sampledRow.get(field));
                    if (value == null || !Double.isFinite(value)) {
                        continue;
                    }
                    hasValidValue = true;

                    if (resolvedGroupBy == null) {
                        List<Double> values = valuesByField.get(field);
                        if (values != null) {
                            values.add(value);
                        }
                        continue;
                    }

                    LinkedHashMap<String, List<Double>> groupedValues = groupedValuesByField.get(field);
                    if (groupedValues == null) {
                        continue;
                    }

                    if (!groupedValues.containsKey(groupValue) && groupedValues.size() >= MAX_BOXPLOT_GROUPS) {
                        continue;
                    }
                    List<Double> values = groupedValues.computeIfAbsent(groupValue, key -> new ArrayList<>());
                    values.add(value);
                }
                if (hasValidValue) {
                    effectiveRowCount++;
                }
            }

            List<BoxplotFieldDataDto> fieldBoxplots = new ArrayList<>();
            if (resolvedGroupBy == null) {
                for (String field : selectedFields) {
                    List<Double> values = valuesByField.getOrDefault(field, List.of());
                    fieldBoxplots.add(buildBoxplotFieldData(field, values, outlierLimit));
                }
            } else {
                for (String field : selectedFields) {
                    Map<String, List<Double>> groupedValues = groupedValuesByField.getOrDefault(field, new LinkedHashMap<>());
                    if (groupedValues.isEmpty()) {
                        fieldBoxplots.add(buildBoxplotFieldData(field, List.of(), outlierLimit));
                        continue;
                    }

                    for (Map.Entry<String, List<Double>> entry : groupedValues.entrySet()) {
                        String groupedFieldName = field + " [" + resolvedGroupBy + "=" + entry.getKey() + "]";
                        fieldBoxplots.add(buildBoxplotFieldData(groupedFieldName, entry.getValue(), outlierLimit));
                    }
                }
            }

            return new BoxplotDataResponseDto(
                    datasetContext.datasetKey(),
                    datasetContext.datasetName(),
                    datasetContext.displayName(),
                    datasetContext.sourceCollection(),
                    queryScope.appliedMatchFilterAsMap(),
                    new DataExplorationCurrentRangeDto(from.toString(), to.toString()),
                    from.toString(),
                    to.toString(),
                    maxRows,
                    totalRowCount,
                    effectiveRowCount,
                    sampledRows.size(),
                    samplingStep > 1L,
                    samplingStep,
                    List.copyOf(fieldBoxplots)
            );
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if (message != null && message.startsWith(BOXPLOT_INVALID_REQUEST_MESSAGE)) {
                throw exception;
            }
            throw invalidBoxplotRequest(message);
        } catch (NullPointerException exception) {
            log.warn("Boxplot query failed due to null data in request/projection.", exception);
            throw invalidBoxplotRequest(null);
        }
    }

    private HistogramFieldListResponseDto buildFieldListResponse(
            DataExplorationDatasetResolver.DatasetContext datasetContext,
            DatasetQueryScope queryScope,
            String from,
            String to,
            boolean includeBoxplotGroupByFields
    ) {
        String sourceCollection = datasetContext.sourceCollection();
        DataExplorationRepository.TimestampRange timestampRange = dataExplorationRepository.findTimestampRange(
                sourceCollection,
                queryScope.appliedMatchFilter()
        );
        if (timestampRange == null || timestampRange.minTimestamp() == null || timestampRange.maxTimestamp() == null) {
            List<String> emptyGroupByFields = includeBoxplotGroupByFields
                    ? resolveBoxplotGroupByFields(datasetContext, List.of())
                    : List.of();
            return new HistogramFieldListResponseDto(
                    datasetContext.datasetKey(),
                    datasetContext.datasetName(),
                    datasetContext.displayName(),
                    datasetContext.sourceCollection(),
                    queryScope.appliedMatchFilterAsMap(),
                    new DataExplorationCurrentRangeDto(null, null),
                    null,
                    null,
                    null,
                    null,
                    0L,
                    List.of(),
                    List.of(),
                    emptyGroupByFields,
                    emptyGroupByFields.isEmpty() ? null : emptyGroupByFields.get(0)
            );
        }

        Instant minTimestamp = timestampRange.minTimestamp().toInstant();
        Instant maxTimestamp = timestampRange.maxTimestamp().toInstant();

        Instant requestFrom = parseOptionalInstant(from, "from");
        Instant requestTo = parseOptionalInstant(to, "to");
        ResolvedQueryRange queryRange = resolveQueryRange(minTimestamp, maxTimestamp, requestFrom, requestTo);

        Date fromDate = Date.from(queryRange.from());
        Date toDate = Date.from(queryRange.to());

        long rowCount = dataExplorationRepository.countRowsByTimestampRange(
                sourceCollection,
                fromDate,
                toDate,
                queryScope.appliedMatchFilter()
        );
        List<Document> sampleRows = rowCount == 0
                ? List.of()
                : dataExplorationRepository.findSampleRowsByTimestampRange(
                        sourceCollection,
                        fromDate,
                        toDate,
                        DEFAULT_FIELD_SAMPLE_LIMIT,
                        queryScope.appliedMatchFilter()
                );

        List<ExplorationFieldOption> fieldOptions = resolveExplorationFieldOptions(
                datasetContext,
                sourceCollection,
                fromDate,
                toDate,
                rowCount,
                sampleRows,
                queryScope.appliedMatchFilter()
        );
        List<String> defaultSelectedFields = resolveDefaultSelectedFields(fieldOptions);
        List<String> groupByFields = includeBoxplotGroupByFields
                ? resolveBoxplotGroupByFields(
                        datasetContext,
                        schemaResolver.mergeConfiguredAndDiscoveredColumns(sourceCollection, sampleRows)
                )
                : List.of();

        List<HistogramFieldOptionDto> fieldOptionDtos = fieldOptions.stream()
                .map(field -> new HistogramFieldOptionDto(
                        field.field(),
                        field.field(),
                        field.numeric(),
                        field.sampleCount(),
                        field.nonNullCount(),
                        field.nullCount(),
                        field.hasValue(),
                        defaultSelectedFields.contains(field.field()),
                        field.sequenceField()
                ))
                .toList();

        return new HistogramFieldListResponseDto(
                datasetContext.datasetKey(),
                datasetContext.datasetName(),
                datasetContext.displayName(),
                datasetContext.sourceCollection(),
                queryScope.appliedMatchFilterAsMap(),
                new DataExplorationCurrentRangeDto(queryRange.from().toString(), queryRange.to().toString()),
                minTimestamp.toString(),
                maxTimestamp.toString(),
                queryRange.from().toString(),
                queryRange.to().toString(),
                rowCount,
                fieldOptionDtos,
                defaultSelectedFields,
                groupByFields,
                groupByFields.isEmpty() ? null : groupByFields.get(0)
        );
    }

    private ResolvedQueryRange resolveQueryRange(
            Instant minTimestamp,
            Instant maxTimestamp,
            Instant requestFrom,
            Instant requestTo
    ) {
        Instant resolvedFrom = requestFrom;
        Instant resolvedTo = requestTo;

        if (resolvedFrom == null && resolvedTo == null) {
            resolvedTo = maxTimestamp;
            Instant lookbackFrom = maxTimestamp.minus(DEFAULT_LOOKBACK_DURATION);
            resolvedFrom = lookbackFrom.isBefore(minTimestamp) ? minTimestamp : lookbackFrom;
        } else {
            if (resolvedFrom == null) {
                resolvedFrom = minTimestamp;
            }
            if (resolvedTo == null) {
                resolvedTo = maxTimestamp;
            }
        }

        validateDateRange(resolvedFrom, resolvedTo);
        return new ResolvedQueryRange(resolvedFrom, resolvedTo);
    }

    private List<ExplorationFieldOption> resolveExplorationFieldOptions(
            DataExplorationDatasetResolver.DatasetContext datasetContext,
            String sourceCollection,
            Date fromDate,
            Date toDate,
            long rowCount,
            List<Document> sampleRows,
            Document additionalMatchFilter
    ) {
        List<String> candidateColumns = schemaResolver.mergeConfiguredAndDiscoveredColumns(
                sourceCollection,
                sampleRows
        );
        if (candidateColumns.isEmpty()) {
            return List.of();
        }

        List<String> filteredCandidates = new ArrayList<>();
        for (String candidate : candidateColumns) {
            String normalized = normalizeFieldName(candidate);
            if (normalized == null || isExcludedChartField(datasetContext, normalized)) {
                continue;
            }
            if (!containsIgnoreCase(filteredCandidates, normalized)) {
                filteredCandidates.add(normalized);
            }
        }

        if (filteredCandidates.isEmpty()) {
            return List.of();
        }

        List<String> numericColumns = schemaResolver.resolveNumericColumns(sampleRows, List.copyOf(filteredCandidates));
        List<String> includedFields = new ArrayList<>();
        for (String candidate : filteredCandidates) {
            if (containsIgnoreCase(numericColumns, candidate)
                    || isProcessValueField(candidate)
                    || isStatusCategoryField(candidate)
                    || isSequenceField(candidate)) {
                includedFields.add(candidate);
            }
        }
        if (includedFields.isEmpty()) {
            includedFields.addAll(numericColumns);
        }

        if (includedFields.isEmpty()) {
            return List.of();
        }

        List<ExplorationFieldOption> resolvedOptions = new ArrayList<>(includedFields.size());
        for (String field : includedFields) {
            DataExplorationRepository.HistogramFieldStats stats = dataExplorationRepository.aggregateFieldStats(
                    sourceCollection,
                    fromDate,
                    toDate,
                    field,
                    additionalMatchFilter
            );

            long nonNullCount = Math.max(0L, stats.sampleCount());
            long sampleCount = Math.max(rowCount, nonNullCount);
            long nullCount = Math.max(0L, sampleCount - nonNullCount);
            boolean hasValue = nonNullCount > 0L;
            boolean numeric = hasValue || containsIgnoreCase(numericColumns, field) || isProcessValueField(field);

            resolvedOptions.add(new ExplorationFieldOption(
                    field,
                    numeric,
                    sampleCount,
                    nonNullCount,
                    nullCount,
                    hasValue,
                    isSequenceField(field)
            ));
        }

        return sortExplorationFields(resolvedOptions);
    }

    private List<String> resolveDefaultSelectedFields(List<ExplorationFieldOption> fieldOptions) {
        if (fieldOptions == null || fieldOptions.isEmpty()) {
            return List.of();
        }

        List<String> preferredProcessFields = fieldOptions.stream()
                .filter(ExplorationFieldOption::hasValue)
                .map(ExplorationFieldOption::field)
                .filter(this::isProcessValueField)
                .toList();
        if (!preferredProcessFields.isEmpty()) {
            return List.copyOf(preferredProcessFields.stream().limit(MAX_SELECTED_FIELDS).toList());
        }

        List<String> nonStatusFields = fieldOptions.stream()
                .filter(ExplorationFieldOption::hasValue)
                .map(ExplorationFieldOption::field)
                .filter(field -> !isStatusCategoryField(field))
                .toList();
        if (!nonStatusFields.isEmpty()) {
            return List.copyOf(nonStatusFields.stream().limit(MAX_SELECTED_FIELDS).toList());
        }

        List<String> fallbackFields = fieldOptions.stream()
                .filter(ExplorationFieldOption::hasValue)
                .map(ExplorationFieldOption::field)
                .toList();
        if (fallbackFields.isEmpty()) {
            return List.of();
        }

        return List.copyOf(fallbackFields.stream().limit(MAX_SELECTED_FIELDS).toList());
    }

    private List<String> normalizeSelectedFields(
            List<String> selectedFields,
            DataExplorationDatasetResolver.DatasetContext datasetContext
    ) {
        if (selectedFields == null || selectedFields.isEmpty()) {
            throw new IllegalArgumentException("selected_fields must not be empty.");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String selectedField : selectedFields) {
            String field = normalizeFieldName(selectedField);
            if (field == null) {
                continue;
            }
            if (!FIELD_NAME_PATTERN.matcher(field).matches()) {
                throw new IllegalArgumentException("selected_fields contains invalid field name: " + field);
            }
            if (isExcludedChartField(datasetContext, field)) {
                throw new IllegalArgumentException("selected_fields contains excluded field: " + field);
            }
            normalized.add(field);
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("selected_fields must not be empty.");
        }
        return List.copyOf(normalized);
    }

    private int resolveBins(Integer bins) {
        if (bins == null) {
            return DEFAULT_BINS;
        }
        if (bins <= 0) {
            throw new IllegalArgumentException("bins must be greater than 0.");
        }
        if (bins > MAX_BINS) {
            throw new IllegalArgumentException("bins must be less than or equal to " + MAX_BINS + ".");
        }
        return bins;
    }

    private int resolveTimeseriesMaxPoints(Integer maxPoints) {
        if (maxPoints == null) {
            return DEFAULT_TIMESERIES_POINTS;
        }
        if (maxPoints <= 0) {
            throw new IllegalArgumentException("max_points must be greater than 0.");
        }
        if (maxPoints > MAX_TIMESERIES_POINTS) {
            throw new IllegalArgumentException("max_points must be less than or equal to " + MAX_TIMESERIES_POINTS + ".");
        }
        return maxPoints;
    }

    private int resolveCorrelationMaxRows(Integer maxRows) {
        if (maxRows == null) {
            return DEFAULT_CORRELATION_MAX_ROWS;
        }
        if (maxRows <= 0) {
            throw new IllegalArgumentException("max_rows must be greater than 0.");
        }
        if (maxRows > MAX_CORRELATION_MAX_ROWS) {
            throw new IllegalArgumentException("max_rows must be less than or equal to " + MAX_CORRELATION_MAX_ROWS + ".");
        }
        return maxRows;
    }

    private int resolveBoxplotMaxRows(Integer maxRows) {
        if (maxRows == null) {
            return DEFAULT_BOXPLOT_MAX_ROWS;
        }
        if (maxRows <= 0) {
            throw new IllegalArgumentException("max_rows must be greater than 0.");
        }
        if (maxRows > MAX_BOXPLOT_MAX_ROWS) {
            throw new IllegalArgumentException("max_rows must be less than or equal to " + MAX_BOXPLOT_MAX_ROWS + ".");
        }
        return maxRows;
    }

    private int resolveOutlierLimit(Integer outlierLimit) {
        if (outlierLimit == null) {
            return DEFAULT_BOXPLOT_OUTLIER_LIMIT;
        }
        if (outlierLimit <= 0) {
            throw new IllegalArgumentException("max_outliers_per_field must be greater than 0.");
        }
        if (outlierLimit > MAX_BOXPLOT_OUTLIER_LIMIT) {
            throw new IllegalArgumentException(
                    "max_outliers_per_field must be less than or equal to " + MAX_BOXPLOT_OUTLIER_LIMIT + "."
            );
        }
        return outlierLimit;
    }

    private long resolveSamplingStep(long totalRowCount, int maxPoints) {
        if (totalRowCount <= 0L) {
            return 1L;
        }
        return Math.max(1L, (long) Math.ceil((double) totalRowCount / (double) maxPoints));
    }

    private BoxplotFieldDataDto buildBoxplotFieldData(String fieldName, List<Double> values, int outlierLimit) {
        if (values == null || values.isEmpty()) {
            return new BoxplotFieldDataDto(
                    fieldName,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0L,
                    List.of()
            );
        }

        List<Double> sortedValues = new ArrayList<>(values);
        sortedValues.removeIf(value -> value == null || !Double.isFinite(value));
        if (sortedValues.isEmpty()) {
            return new BoxplotFieldDataDto(
                    fieldName,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0L,
                    List.of()
            );
        }
        Collections.sort(sortedValues);

        long sampleCount = sortedValues.size();
        double min = sortedValues.get(0);
        double max = sortedValues.get(sortedValues.size() - 1);
        double q1 = percentile(sortedValues, 0.25D);
        double median = percentile(sortedValues, 0.5D);
        double q3 = percentile(sortedValues, 0.75D);

        double iqr = q3 - q1;
        if (!Double.isFinite(iqr) || iqr < 0D) {
            iqr = 0D;
        }

        double lowerFence = q1 - (1.5D * iqr);
        double upperFence = q3 + (1.5D * iqr);

        double whiskerLow = min;
        for (double value : sortedValues) {
            if (value >= lowerFence) {
                whiskerLow = value;
                break;
            }
        }

        double whiskerHigh = max;
        for (int index = sortedValues.size() - 1; index >= 0; index--) {
            double value = sortedValues.get(index);
            if (value <= upperFence) {
                whiskerHigh = value;
                break;
            }
        }

        List<Double> outliers = new ArrayList<>();
        long outlierCount = 0L;
        for (double value : sortedValues) {
            if (value < whiskerLow || value > whiskerHigh) {
                outlierCount++;
                if (outliers.size() < outlierLimit) {
                    outliers.add(value);
                }
            }
        }

        return new BoxplotFieldDataDto(
                fieldName,
                sampleCount,
                min,
                q1,
                median,
                q3,
                max,
                whiskerLow,
                whiskerHigh,
                outlierCount,
                List.copyOf(outliers)
        );
    }

    private double percentile(List<Double> sortedValues, double quantile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return Double.NaN;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }

        double clampedQuantile = Math.min(1D, Math.max(0D, quantile));
        double index = clampedQuantile * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }

        double lowerValue = sortedValues.get(lowerIndex);
        double upperValue = sortedValues.get(upperIndex);
        double ratio = index - lowerIndex;
        return lowerValue + (upperValue - lowerValue) * ratio;
    }

    private CorrelationComputation buildCorrelationMatrix(List<String> selectedFields, List<Document> sampledRows) {
        int fieldCount = selectedFields.size();
        int sampledRowCount = sampledRows.size();

        double[][] rowValues = new double[sampledRowCount][fieldCount];
        long effectiveRowCount = 0L;
        for (int rowIndex = 0; rowIndex < sampledRowCount; rowIndex++) {
            Document row = sampledRows.get(rowIndex);
            int validFieldCount = 0;

            for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                Double value = asDouble(row.get(selectedFields.get(fieldIndex)));
                if (value != null && Double.isFinite(value)) {
                    rowValues[rowIndex][fieldIndex] = value;
                    validFieldCount++;
                } else {
                    rowValues[rowIndex][fieldIndex] = Double.NaN;
                }
            }

            if (validFieldCount >= 2) {
                effectiveRowCount++;
            }
        }

        Double[][] correlationMatrix = new Double[fieldCount][fieldCount];
        long[][] pairSampleCounts = new long[fieldCount][fieldCount];

        for (int leftIndex = 0; leftIndex < fieldCount; leftIndex++) {
            long diagonalCount = 0L;
            for (int rowIndex = 0; rowIndex < sampledRowCount; rowIndex++) {
                if (Double.isFinite(rowValues[rowIndex][leftIndex])) {
                    diagonalCount++;
                }
            }
            correlationMatrix[leftIndex][leftIndex] = 1D;
            pairSampleCounts[leftIndex][leftIndex] = diagonalCount;

            for (int rightIndex = leftIndex + 1; rightIndex < fieldCount; rightIndex++) {
                PairwiseAccumulator accumulator = new PairwiseAccumulator();
                for (int rowIndex = 0; rowIndex < sampledRowCount; rowIndex++) {
                    double leftValue = rowValues[rowIndex][leftIndex];
                    double rightValue = rowValues[rowIndex][rightIndex];
                    if (!Double.isFinite(leftValue) || !Double.isFinite(rightValue)) {
                        continue;
                    }
                    accumulator.accept(leftValue, rightValue);
                }

                Double correlation = accumulator.toPearson();
                double safeCorrelation = sanitizeCorrelation(correlation);

                correlationMatrix[leftIndex][rightIndex] = safeCorrelation;
                correlationMatrix[rightIndex][leftIndex] = safeCorrelation;
                pairSampleCounts[leftIndex][rightIndex] = accumulator.count();
                pairSampleCounts[rightIndex][leftIndex] = accumulator.count();
            }
        }

        List<List<Double>> matrixRows = new ArrayList<>(fieldCount);
        List<List<Long>> pairCountRows = new ArrayList<>(fieldCount);
        for (int rowIndex = 0; rowIndex < fieldCount; rowIndex++) {
            List<Double> matrixRow = new ArrayList<>(fieldCount);
            List<Long> pairCountRow = new ArrayList<>(fieldCount);
            for (int columnIndex = 0; columnIndex < fieldCount; columnIndex++) {
                matrixRow.add(sanitizeCorrelation(correlationMatrix[rowIndex][columnIndex]));
                pairCountRow.add(Math.max(0L, pairSampleCounts[rowIndex][columnIndex]));
            }
            matrixRows.add(List.copyOf(matrixRow));
            pairCountRows.add(List.copyOf(pairCountRow));
        }

        return new CorrelationComputation(List.copyOf(matrixRows), List.copyOf(pairCountRows), effectiveRowCount);
    }

    private double sanitizeCorrelation(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return 0D;
        }
        return Math.max(-1D, Math.min(1D, value));
    }

    private List<Double> buildBoundaries(double min, double max, int binCount) {
        double safeMin = Double.isFinite(min) ? min : 0D;
        double safeMax = Double.isFinite(max) ? max : safeMin;

        double range = safeMax - safeMin;
        if (!Double.isFinite(range) || range <= 0D) {
            range = Math.max(Math.abs(safeMin), 1D) * 0.01D;
        }

        double step = range / binCount;
        if (!Double.isFinite(step) || step <= 0D) {
            step = 1D / binCount;
        }

        List<Double> boundaries = new ArrayList<>(binCount + 1);
        boundaries.add(safeMin);

        for (int index = 1; index <= binCount; index++) {
            double previous = boundaries.get(index - 1);
            double boundary = safeMin + step * index;
            if (boundary <= previous) {
                boundary = Math.nextUp(previous);
            }
            boundaries.add(boundary);
        }

        int lastIndex = boundaries.size() - 1;
        if (boundaries.get(lastIndex) <= safeMax) {
            boundaries.set(lastIndex, Math.nextUp(safeMax));
        }

        return boundaries;
    }

    private int resolveBucketIndex(List<Double> boundaries, double bucketStart) {
        for (int index = 0; index < boundaries.size() - 1; index++) {
            double boundary = boundaries.get(index);
            double tolerance = Math.max(Math.abs(boundary), 1D) * EPSILON;
            if (Math.abs(boundary - bucketStart) <= tolerance) {
                return index;
            }
        }
        return -1;
    }

    private String normalizeFieldName(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        String trimmed = fieldName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String asIsoTimestamp(Object value) {
        if (value instanceof Date dateValue) {
            return dateValue.toInstant().toString();
        }
        if (value instanceof Instant instantValue) {
            return instantValue.toString();
        }
        if (value instanceof String textValue) {
            String normalized = textValue.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            try {
                return Instant.parse(normalized).toString();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String textValue) {
            String normalized = textValue.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<String> resolveBoxplotGroupByFields(
            DataExplorationDatasetResolver.DatasetContext datasetContext,
            List<String> availableColumns
    ) {
        List<String> baseCandidates = datasetResolver.isSupervisedDataset(datasetContext)
                ? LABELED_BOXPLOT_GROUP_BY_FIELDS
                : DEFAULT_BOXPLOT_GROUP_BY_FIELDS;

        if (baseCandidates.isEmpty()) {
            return List.of();
        }

        if (availableColumns == null || availableColumns.isEmpty()) {
            return List.of();
        }

        List<String> resolved = new ArrayList<>();
        for (String candidate : baseCandidates) {
            if (containsIgnoreCase(availableColumns, candidate)) {
                resolved.add(candidate);
            }
        }
        return List.copyOf(resolved);
    }

    private String resolveGroupByField(String requestedGroupBy, List<String> allowedGroupByFields) {
        String normalizedRequested = normalizeFieldName(requestedGroupBy);
        if (normalizedRequested == null) {
            return null;
        }

        for (String allowedField : allowedGroupByFields) {
            if (allowedField.equalsIgnoreCase(normalizedRequested)) {
                return allowedField;
            }
        }
        throw new IllegalArgumentException(
                "group_by is unavailable for this dataset/time range: " + normalizedRequested
        );
    }

    private DataExplorationDatasetResolver.DatasetContext resolveBoxplotDatasetContext(String datasetKey) {
        String normalizedDatasetKey = normalizeFieldName(datasetKey);
        try {
            return datasetResolver.resolveDatasetContext(normalizedDatasetKey);
        } catch (IllegalArgumentException exception) {
            if (normalizedDatasetKey == null) {
                throw invalidBoxplotRequest("dataset_key fallback resolution failed.");
            }
            throw invalidBoxplotRequest("dataset_key was not found: " + normalizedDatasetKey);
        }
    }

    private DatasetQueryScope resolveDatasetQueryScope(
            DataExplorationDatasetResolver.DatasetContext datasetContext,
            String requestedEquipmentId
    ) {
        Document datasetMatchFilter = datasetContext == null ? new Document() : datasetContext.copyMatchFilter();
        String datasetMatchEquipmentId = resolveMatchFilterEquipmentId(datasetMatchFilter);
        String normalizedRequestedEquipmentId = normalizeEquipmentId(requestedEquipmentId);

        if (datasetMatchEquipmentId != null
                && normalizedRequestedEquipmentId != null
                && !datasetMatchEquipmentId.equalsIgnoreCase(normalizedRequestedEquipmentId)) {
            throw new IllegalArgumentException(
                    "?좏깮??dataset? "
                            + datasetMatchEquipmentId
                            + "?몃뜲 ?낅젰 MCCODE??"
                            + normalizedRequestedEquipmentId
                            + "?낅땲??"
            );
        }

        Document appliedMatchFilter = cloneDocument(datasetMatchFilter);
        if (normalizedRequestedEquipmentId != null && datasetMatchEquipmentId == null) {
            Document requestedEquipmentFilter = new Document(EQUIPMENT_FIELD, normalizedRequestedEquipmentId);
            if (appliedMatchFilter == null || appliedMatchFilter.isEmpty()) {
                appliedMatchFilter = requestedEquipmentFilter;
            } else {
                appliedMatchFilter = new Document(
                        "$and",
                        List.of(
                                cloneDocument(appliedMatchFilter),
                                requestedEquipmentFilter
                        )
                );
            }
        }

        if (appliedMatchFilter == null) {
            appliedMatchFilter = new Document();
        }
        return new DatasetQueryScope(appliedMatchFilter);
    }

    private String resolveMatchFilterEquipmentId(Document matchFilter) {
        return resolveMatchFilterEquipmentIdFromValue(matchFilter);
    }

    private String resolveMatchFilterEquipmentIdFromValue(Object filterValue) {
        if (filterValue instanceof Document documentValue) {
            return resolveMatchFilterEquipmentIdFromMap(documentValue);
        }
        if (filterValue instanceof Map<?, ?> mapValue) {
            return resolveMatchFilterEquipmentIdFromMap(mapValue);
        }
        if (filterValue instanceof List<?> listValue) {
            for (Object item : listValue) {
                String nested = resolveMatchFilterEquipmentIdFromValue(item);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String resolveMatchFilterEquipmentIdFromMap(Map<?, ?> mapValue) {
        if (mapValue == null || mapValue.isEmpty()) {
            return null;
        }

        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
            if (key == null || key.isBlank()) {
                continue;
            }

            if (EQUIPMENT_FIELD.equalsIgnoreCase(key)) {
                String resolved = resolveEquipmentIdFromFilterValue(entry.getValue());
                if (resolved != null) {
                    return resolved;
                }
            }

            String nested = resolveMatchFilterEquipmentIdFromValue(entry.getValue());
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private String resolveEquipmentIdFromFilterValue(Object rawValue) {
        if (rawValue instanceof String textValue) {
            return normalizeEquipmentId(textValue);
        }
        if (rawValue instanceof Document valueDocument) {
            Object eqValue = valueDocument.get("$eq");
            if (eqValue instanceof String textEqValue) {
                return normalizeEquipmentId(textEqValue);
            }
            Object inValue = valueDocument.get("$in");
            if (inValue instanceof List<?> listValue) {
                for (Object candidate : listValue) {
                    if (candidate instanceof String textCandidate) {
                        return normalizeEquipmentId(textCandidate);
                    }
                }
            }
        }
        if (rawValue instanceof Map<?, ?> valueMap) {
            Object eqValue = valueMap.get("$eq");
            if (eqValue instanceof String textEqValue) {
                return normalizeEquipmentId(textEqValue);
            }
            Object inValue = valueMap.get("$in");
            if (inValue instanceof List<?> listValue) {
                for (Object candidate : listValue) {
                    if (candidate instanceof String textCandidate) {
                        return normalizeEquipmentId(textCandidate);
                    }
                }
            }
        }
        return null;
    }

    private String normalizeEquipmentId(String equipmentId) {
        String normalized = normalizeFieldName(equipmentId);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private Document cloneDocument(Document source) {
        if (source == null || source.isEmpty()) {
            return source == null ? null : new Document();
        }
        Document clone = new Document();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            clone.put(entry.getKey(), cloneValue(entry.getValue()));
        }
        return clone;
    }

    private Object cloneValue(Object value) {
        if (value instanceof Document documentValue) {
            return cloneDocument(documentValue);
        }
        if (value instanceof Map<?, ?> mapValue) {
            Document document = new Document();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                document.put(String.valueOf(entry.getKey()), cloneValue(entry.getValue()));
            }
            return document;
        }
        if (value instanceof List<?> listValue) {
            List<Object> cloned = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                cloned.add(cloneValue(item));
            }
            return cloned;
        }
        return value;
    }

    private void validateSelectedFieldsForBoxplot(List<String> selectedFields, List<String> availableColumns) {
        List<String> missingFields = new ArrayList<>();
        for (String selectedField : selectedFields) {
            if (containsIgnoreCase(availableColumns, selectedField)) {
                continue;
            }
            missingFields.add(selectedField);
        }
        if (!missingFields.isEmpty()) {
            throw invalidBoxplotRequest(
                    "selected_fields contains fields that do not exist in this dataset: " + String.join(", ", missingFields)
            );
        }
    }

    private IllegalArgumentException invalidBoxplotRequest(String detailMessage) {
        if (detailMessage == null || detailMessage.isBlank()) {
            return new IllegalArgumentException(BOXPLOT_INVALID_REQUEST_MESSAGE);
        }
        return new IllegalArgumentException(BOXPLOT_INVALID_REQUEST_MESSAGE + " " + detailMessage);
    }

    private String resolveGroupValueLabel(Object value) {
        if (value == null) {
            return BOX_GROUP_NULL_LABEL;
        }
        if (value instanceof String textValue) {
            String normalized = textValue.trim();
            return normalized.isEmpty() ? BOX_GROUP_NULL_LABEL : normalized;
        }
        return String.valueOf(value);
    }

    private boolean isExcludedChartField(
            DataExplorationDatasetResolver.DatasetContext datasetContext,
            String fieldName
    ) {
        if (isExcludedMetaField(fieldName)) {
            return true;
        }
        if (!datasetResolver.isSupervisedDataset(datasetContext)) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return SUPERVISED_EXCLUDED_FIELDS_LOWER.contains(normalized);
    }

    private boolean isExcludedMetaField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return META_EXCLUDED_FIELDS_LOWER.contains(normalized);
    }

    private boolean isSequenceField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return SEQUENCE_CANDIDATE_FIELDS_LOWER.contains(normalized);
    }

    private List<ExplorationFieldOption> sortExplorationFields(List<ExplorationFieldOption> fieldOptions) {
        if (fieldOptions == null || fieldOptions.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> originIndex = new LinkedHashMap<>();
        for (int index = 0; index < fieldOptions.size(); index++) {
            originIndex.putIfAbsent(fieldOptions.get(index).field(), index);
        }

        List<ExplorationFieldOption> sorted = new ArrayList<>(fieldOptions);
        sorted.sort(
                Comparator.comparingInt((ExplorationFieldOption option) -> resolveExplorationFieldPriority(option.field()))
                        .thenComparingInt(option -> originIndex.getOrDefault(option.field(), Integer.MAX_VALUE))
        );
        return List.copyOf(sorted);
    }

    private int resolveExplorationFieldPriority(String fieldName) {
        if (isPvField(fieldName)) {
            return 0;
        }
        if (isSvField(fieldName)) {
            return 1;
        }
        if (isStatusCategoryField(fieldName) || isSequenceField(fieldName)) {
            return 3;
        }
        return 2;
    }

    private boolean isProcessValueField(String fieldName) {
        return isPvField(fieldName) || isSvField(fieldName);
    }

    private boolean isPvField(String fieldName) {
        String normalized = normalizeFieldName(fieldName);
        if (normalized == null) {
            return false;
        }
        return normalized.toUpperCase(Locale.ROOT).endsWith(PV_SUFFIX);
    }

    private boolean isSvField(String fieldName) {
        String normalized = normalizeFieldName(fieldName);
        if (normalized == null) {
            return false;
        }
        return normalized.toUpperCase(Locale.ROOT).endsWith(SV_SUFFIX);
    }

    private boolean isStatusCategoryField(String fieldName) {
        String normalized = normalizeFieldName(fieldName);
        if (normalized == null) {
            return false;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        String lower = normalized.toLowerCase(Locale.ROOT);
        return STATUS_CATEGORY_FIELDS_LOWER.contains(lower)
                || upper.startsWith("SEGMENT_")
                || upper.endsWith("_STAT")
                || upper.contains("OPALARM")
                || upper.contains("OPSTAT");
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || values.isEmpty() || target == null || target.isBlank()) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private Instant parseRequiredInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return parseIsoInstant(value, fieldName);
    }

    private Instant parseOptionalInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseIsoInstant(value, fieldName);
    }

    private Instant parseIsoInstant(String value, String fieldName) {
        String normalized = value.trim();
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    fieldName + " must be a valid UTC ISO instant (e.g. 2026-04-01T00:00:00Z)."
            );
        }
    }

    private void validateDateRange(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be earlier than or equal to to.");
        }
    }

    private record ExplorationFieldOption(
            String field,
            boolean numeric,
            long sampleCount,
            long nonNullCount,
            long nullCount,
            boolean hasValue,
            boolean sequenceField
    ) {
    }

    private record DatasetQueryScope(Document appliedMatchFilter) {
        Map<String, Object> appliedMatchFilterAsMap() {
            if (appliedMatchFilter == null || appliedMatchFilter.isEmpty()) {
                return Map.of();
            }
            return Map.copyOf(new LinkedHashMap<>(cloneDocumentStatic(appliedMatchFilter)));
        }

        private static Document cloneDocumentStatic(Document source) {
            if (source == null || source.isEmpty()) {
                return source == null ? new Document() : new Document(source);
            }
            Document clone = new Document();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                clone.put(entry.getKey(), cloneValueStatic(entry.getValue()));
            }
            return clone;
        }

        private static Object cloneValueStatic(Object value) {
            if (value instanceof Document documentValue) {
                return cloneDocumentStatic(documentValue);
            }
            if (value instanceof Map<?, ?> mapValue) {
                Document document = new Document();
                for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    document.put(String.valueOf(entry.getKey()), cloneValueStatic(entry.getValue()));
                }
                return document;
            }
            if (value instanceof List<?> listValue) {
                List<Object> cloned = new ArrayList<>(listValue.size());
                for (Object item : listValue) {
                    cloned.add(cloneValueStatic(item));
                }
                return cloned;
            }
            return value;
        }
    }

    private static final class PairwiseAccumulator {
        private long count;
        private double sumX;
        private double sumY;
        private double sumXX;
        private double sumYY;
        private double sumXY;

        void accept(double x, double y) {
            count++;
            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumYY += y * y;
            sumXY += x * y;
        }

        long count() {
            return count;
        }

        Double toPearson() {
            if (count < 2) {
                return null;
            }

            double varianceXTerm = count * sumXX - (sumX * sumX);
            double varianceYTerm = count * sumYY - (sumY * sumY);
            if (!Double.isFinite(varianceXTerm) || !Double.isFinite(varianceYTerm)) {
                return null;
            }
            if (varianceXTerm <= EPSILON || varianceYTerm <= EPSILON) {
                return null;
            }

            double denominator = Math.sqrt(varianceXTerm * varianceYTerm);
            if (!Double.isFinite(denominator) || denominator <= EPSILON) {
                return null;
            }

            double numerator = count * sumXY - (sumX * sumY);
            double correlation = numerator / denominator;
            if (!Double.isFinite(correlation)) {
                return null;
            }
            return Math.max(-1D, Math.min(1D, correlation));
        }
    }

    private record ResolvedQueryRange(Instant from, Instant to) {
    }

    private record CorrelationComputation(
            List<List<Double>> matrix,
            List<List<Long>> pairSampleCounts,
            long effectiveRowCount
    ) {
    }
}
