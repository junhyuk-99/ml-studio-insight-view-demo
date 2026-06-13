package com.demo.insight.dataexploration.service;

import com.demo.insight.dataexploration.dto.ProcessFlowQueryDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto.ProcessFlowCurrentStateDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto.ProcessFlowEventDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto.ProcessFlowLatestDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto.ProcessFlowOpstatMappingDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto.ProcessFlowRangeDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto.ProcessFlowSegmentDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto.ProcessFlowStageSummaryDto;
import com.demo.insight.dataexploration.dto.ProcessFlowResponseDto.ProcessFlowTemperaturePointDto;
import com.demo.insight.dataexploration.repository.ProcessFlowRepository;
import com.demo.insight.equipment.dto.EquipmentMasterDto;
import com.demo.insight.equipment.service.EquipmentMasterService;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProcessFlowServiceImpl implements ProcessFlowService {

    private static final Logger log = LoggerFactory.getLogger(ProcessFlowServiceImpl.class);

    private static final String PROJECT_TIMEZONE = "Asia/Seoul";
    private static final String SUPPORTED_GROUP_1 = "PES_OPSTATUS1";
    private static final String SUPPORTED_GROUP_2 = "PES_OPSTATUS2";
    private static final int DEFAULT_LIMIT = 5000;
    private static final int MAX_LIMIT = 10000;
    private static final int MAX_EVENTS = 500;
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private static final List<String> BASE_PROJECTED_FIELDS = List.of(
            "PRDTIME",
            "MCCODE",
            "OPSTAT",
            "OPALARM",
            "WORKORDER",
            "WORK_ORDER",
            "PAT_PGM",
            "PATPGM",
            "SEGMENT_NO",
            "SEGMENT_TIME",
            "SEGMENT_TOTAL",
            "T1_PV",
            "T2_PV",
            "T1_SV",
            "T2_SV",
            "CO2_PV",
            "CO_PV",
            "CP_PV"
    );

    private static final List<OpstatDefinition> OPSTATUS_1_DEFINITIONS = List.of(
            new OpstatDefinition(0, "Not Started"),
            new OpstatDefinition(1, "Preheat Start"),
            new OpstatDefinition(2, "Hold"),
            new OpstatDefinition(4, "Crack Check"),
            new OpstatDefinition(8, "Carburizing"),
            new OpstatDefinition(16, "Diffusion"),
            new OpstatDefinition(32, "Cooling"),
            new OpstatDefinition(64, "Final Check")
    );

    private static final List<OpstatDefinition> OPSTATUS_2_DEFINITIONS = List.of(
            new OpstatDefinition(0, "Not Started"),
            new OpstatDefinition(1, "Hold"),
            new OpstatDefinition(2, "Crack Operation"),
            new OpstatDefinition(4, "Carburizing"),
            new OpstatDefinition(8, "Diffusion"),
            new OpstatDefinition(16, "Cooling"),
            new OpstatDefinition(32, "Final Check"),
            new OpstatDefinition(64, "Completed")
    );

    private final ProcessFlowRepository processFlowRepository;
    private final DataExplorationDatasetResolver datasetResolver;
    private final EquipmentMasterService equipmentMasterService;

    public ProcessFlowServiceImpl(
            ProcessFlowRepository processFlowRepository,
            DataExplorationDatasetResolver datasetResolver,
            EquipmentMasterService equipmentMasterService
    ) {
        this.processFlowRepository = processFlowRepository;
        this.datasetResolver = datasetResolver;
        this.equipmentMasterService = equipmentMasterService;
    }

    @Override
    public ProcessFlowResponseDto getProcessFlow(ProcessFlowQueryDto query) {
        if (query == null) {
            throw new IllegalArgumentException("Process flow query is required.");
        }

        List<String> warnings = new ArrayList<>();
        DataExplorationDatasetResolver.DatasetContext datasetContext =
                datasetResolver.resolveDatasetContext(query.datasetKey());

        if (!ProcessFlowRepository.SOURCE_COLLECTION.equalsIgnoreCase(datasetContext.sourceCollection())) {
            warnings.add(
                    "Process flow uses raw THISHMIDATA only. Dataset source "
                            + datasetContext.sourceCollection()
                            + " was ignored."
            );
        }

        String mccode = resolveMccode(query.mccode(), datasetContext, warnings);
        EquipmentMasterDto equipment = resolveActiveEquipment(mccode);
        if (equipment == null) {
            warnings.add("TMSTMC active equipment metadata was not found for " + mccode + ".");
        }

        String opstatGroup = resolveOpstatGroup(equipment, mccode, warnings);
        List<ProcessFlowOpstatMappingDto> baseMapping = resolveBaseMapping(opstatGroup);
        List<Integer> selectedOpstats = parseOpstats(query.opstats(), warnings);
        List<Object> opstatFilterValues = buildOpstatQueryValues(selectedOpstats);
        Set<String> projectedFields = resolveProjectedFields(query.fields(), warnings);
        int limit = resolveLimit(query.limit());

        Instant start = parseRequiredInstant(query.start(), "start");
        Instant end = parseRequiredInstant(query.end(), "end");
        validateRange(start, end);

        Date startDate = Date.from(start);
        Date endDate = Date.from(end);
        long totalRowCount = processFlowRepository.countRows(mccode, startDate, endDate, opstatFilterValues);
        if (totalRowCount > limit) {
            warnings.add(
                    "Query matched "
                            + totalRowCount
                            + " rows; only the first "
                            + limit
                            + " rows are returned by PRDTIME."
            );
        }

        List<Document> documents = processFlowRepository.findRows(
                mccode,
                startDate,
                endDate,
                opstatFilterValues,
                projectedFields,
                limit
        );
        List<ProcessFlowRawRow> rows = documents.stream()
                .map(this::toRawRow)
                .filter(row -> row.prdtime() != null)
                .sorted(Comparator.comparing(ProcessFlowRawRow::prdtime))
                .toList();

        Set<Integer> observedCodes = new LinkedHashSet<>();
        for (ProcessFlowRawRow row : rows) {
            if (row.opstat() != null) {
                observedCodes.add(row.opstat());
            }
        }

        List<ProcessFlowOpstatMappingDto> opstatMapping = mergeObservedMappings(baseMapping, observedCodes);
        Map<Integer, ProcessFlowOpstatMappingDto> mappingByCode = mapByCode(opstatMapping);
        List<SegmentAccumulator> segments = buildSegments(rows);
        List<ProcessFlowTemperaturePointDto> temperatureSeries = rows.stream()
                .map(row -> toTemperaturePoint(row, mappingByCode))
                .toList();

        ProcessFlowRawRow latestRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        SegmentAccumulator currentSegment = segments.isEmpty() ? null : segments.get(segments.size() - 1);

        List<ProcessFlowEventDto> eventTimeline = buildEventTimeline(segments, rows, mappingByCode, warnings);

        return new ProcessFlowResponseDto(
                datasetContext.datasetKey(),
                ProcessFlowRepository.SOURCE_COLLECTION,
                mccode,
                equipment == null || equipment.mcname() == null ? mccode : equipment.mcname(),
                opstatGroup,
                opstatMapping,
                new ProcessFlowRangeDto(start.toString(), end.toString(), PROJECT_TIMEZONE),
                latestRow == null ? null : toLatest(latestRow, mappingByCode),
                currentSegment == null || latestRow == null
                        ? null
                        : toCurrentState(currentSegment, latestRow, mappingByCode),
                temperatureSeries,
                toSegmentDtos(segments, mappingByCode),
                toStageSummaryDtos(segments, mappingByCode),
                eventTimeline,
                List.copyOf(warnings)
        );
    }

    private String resolveMccode(
            String requestedMccode,
            DataExplorationDatasetResolver.DatasetContext datasetContext,
            List<String> warnings
    ) {
        String normalizedRequested = normalizeMccode(requestedMccode);
        String datasetMccode = resolveMatchFilterMccode(datasetContext.matchFilterAsMap());

        if (normalizedRequested != null && datasetMccode != null && !normalizedRequested.equals(datasetMccode)) {
            warnings.add(
                    "Dataset match_filter MCCODE "
                            + datasetMccode
                            + " was ignored; process flow uses selected MCCODE "
                            + normalizedRequested
                            + "."
            );
        }
        if (normalizedRequested != null) {
            return normalizedRequested;
        }
        if (datasetMccode != null) {
            warnings.add("MCCODE was resolved from dataset match_filter: " + datasetMccode + ".");
            return datasetMccode;
        }

        String defaultMccode = normalizeMccode(equipmentMasterService.resolveDefaultOperationalEquipmentId());
        if (defaultMccode != null) {
            warnings.add("MCCODE was not supplied; using default operational equipment " + defaultMccode + ".");
            return defaultMccode;
        }

        throw new IllegalArgumentException("mccode is required.");
    }

    private EquipmentMasterDto resolveActiveEquipment(String mccode) {
        if (mccode == null) {
            return null;
        }
        for (EquipmentMasterDto equipment : equipmentMasterService.getActiveEquipments()) {
            String equipmentCode = normalizeMccode(equipment.mccode());
            if (mccode.equals(equipmentCode)) {
                return equipment;
            }
        }
        return null;
    }

    private String resolveOpstatGroup(
            EquipmentMasterDto equipment,
            String mccode,
            List<String> warnings
    ) {
        String configuredGroup = normalizeUpper(equipment == null ? null : equipment.opstatCodeGroup());
        if (SUPPORTED_GROUP_1.equals(configuredGroup) || SUPPORTED_GROUP_2.equals(configuredGroup)) {
            return configuredGroup;
        }
        if (configuredGroup != null) {
            warnings.add(
                    "TMSTMC.opstat_code_group is unsupported for "
                            + mccode
                            + ": "
                            + configuredGroup
                            + ". Raw OPSTAT labels are used."
            );
            return configuredGroup;
        }

        String fallbackGroup = resolveFallbackGroupByMccode(mccode);
        if (fallbackGroup != null) {
            String warning = "TMSTMC.opstat_code_group is missing for "
                    + mccode
                    + "; using temporary server fallback "
                    + fallbackGroup
                    + ".";
            log.warn(warning);
            warnings.add(warning);
            return fallbackGroup;
        }

        warnings.add(
                "OPSTAT group is not configured for "
                        + mccode
                        + ". Generic OPSTAT labels are used."
        );
        return null;
    }

    private String resolveFallbackGroupByMccode(String mccode) {
        String normalized = normalizeMccode(mccode);
        if (normalized == null) {
            return null;
        }

        // TODO: replace this fallback with tmst_code/code-master lookup once the approved Mongo code master exists.
        return switch (normalized) {
            case "DEMO-MC-001", "DEMO-MC-002" -> SUPPORTED_GROUP_1;
            case "DEMO-MC-003" -> SUPPORTED_GROUP_2;
            default -> null;
        };
    }

    private List<ProcessFlowOpstatMappingDto> resolveBaseMapping(String opstatGroup) {
        if (SUPPORTED_GROUP_1.equals(opstatGroup)) {
            return toMapping(OPSTATUS_1_DEFINITIONS);
        }
        if (SUPPORTED_GROUP_2.equals(opstatGroup)) {
            return toMapping(OPSTATUS_2_DEFINITIONS);
        }
        return List.of();
    }

    private List<ProcessFlowOpstatMappingDto> toMapping(List<OpstatDefinition> definitions) {
        List<ProcessFlowOpstatMappingDto> mapping = new ArrayList<>(definitions.size());
        for (int index = 0; index < definitions.size(); index++) {
            OpstatDefinition definition = definitions.get(index);
            mapping.add(new ProcessFlowOpstatMappingDto(
                    definition.code(),
                    definition.label(),
                    index,
                    "state" + index
            ));
        }
        return List.copyOf(mapping);
    }

    private List<ProcessFlowOpstatMappingDto> mergeObservedMappings(
            List<ProcessFlowOpstatMappingDto> baseMapping,
            Set<Integer> observedCodes
    ) {
        Map<Integer, ProcessFlowOpstatMappingDto> merged = new LinkedHashMap<>();
        for (ProcessFlowOpstatMappingDto mapping : baseMapping) {
            if (mapping.code() == null) {
                continue;
            }
            merged.put(mapping.code(), mapping);
        }

        List<Integer> sortedObserved = observedCodes.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        for (Integer observedCode : sortedObserved) {
            if (merged.containsKey(observedCode)) {
                continue;
            }
            int sortno = merged.size();
            merged.put(observedCode, new ProcessFlowOpstatMappingDto(
                    observedCode,
                    "OPSTAT " + observedCode,
                    sortno,
                    "state" + Math.min(sortno, 8)
            ));
        }
        return List.copyOf(merged.values());
    }

    private Map<Integer, ProcessFlowOpstatMappingDto> mapByCode(List<ProcessFlowOpstatMappingDto> mappings) {
        Map<Integer, ProcessFlowOpstatMappingDto> mapped = new LinkedHashMap<>();
        for (ProcessFlowOpstatMappingDto mapping : mappings) {
            if (mapping.code() != null) {
                mapped.put(mapping.code(), mapping);
            }
        }
        return Map.copyOf(mapped);
    }

    private List<Integer> parseOpstats(String rawOpstats, List<String> warnings) {
        String normalized = normalizeOptionalText(rawOpstats);
        if (normalized == null) {
            return List.of();
        }

        LinkedHashSet<Integer> parsed = new LinkedHashSet<>();
        for (String token : normalized.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                parsed.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                warnings.add("Invalid OPSTAT filter was ignored: " + trimmed + ".");
            }
        }
        return List.copyOf(parsed);
    }

    private List<Object> buildOpstatQueryValues(List<Integer> opstats) {
        if (opstats == null || opstats.isEmpty()) {
            return List.of();
        }
        List<Object> values = new ArrayList<>();
        for (Integer opstat : opstats) {
            if (opstat == null) {
                continue;
            }
            values.add(opstat);
            values.add(opstat.longValue());
            values.add(String.valueOf(opstat));
        }
        return List.copyOf(values);
    }

    private Set<String> resolveProjectedFields(String rawFields, List<String> warnings) {
        LinkedHashSet<String> projected = new LinkedHashSet<>(BASE_PROJECTED_FIELDS);
        String normalized = normalizeOptionalText(rawFields);
        if (normalized == null) {
            return Set.copyOf(projected);
        }

        for (String token : normalized.split(",")) {
            String field = normalizeOptionalText(token);
            if (field == null) {
                continue;
            }
            if (!FIELD_NAME_PATTERN.matcher(field).matches()) {
                warnings.add("Invalid field projection was ignored: " + field + ".");
                continue;
            }
            projected.add(field);
        }
        return Set.copyOf(projected);
    }

    private int resolveLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, Math.max(1, requestedLimit));
    }

    private ProcessFlowRawRow toRawRow(Document document) {
        return new ProcessFlowRawRow(
                asInstant(valueOf(document, "PRDTIME")),
                asInteger(valueOf(document, "OPSTAT")),
                asString(valueOf(document, "OPALARM")),
                asString(valueOf(document, "WORKORDER", "WORK_ORDER")),
                asString(valueOf(document, "PAT_PGM", "PATPGM")),
                asInteger(valueOf(document, "SEGMENT_NO")),
                asInteger(valueOf(document, "SEGMENT_TIME")),
                asInteger(valueOf(document, "SEGMENT_TOTAL")),
                asDouble(valueOf(document, "T1_PV")),
                asDouble(valueOf(document, "T2_PV")),
                asDouble(valueOf(document, "T1_SV")),
                asDouble(valueOf(document, "T2_SV")),
                asDouble(valueOf(document, "CO2_PV")),
                asDouble(valueOf(document, "CO_PV")),
                asDouble(valueOf(document, "CP_PV"))
        );
    }

    private List<SegmentAccumulator> buildSegments(List<ProcessFlowRawRow> rows) {
        List<SegmentAccumulator> segments = new ArrayList<>();
        SegmentAccumulator current = null;
        for (ProcessFlowRawRow row : rows) {
            if (current == null || !Objects.equals(current.opstat(), row.opstat())) {
                current = new SegmentAccumulator(row.opstat(), row.prdtime());
                segments.add(current);
            }
            current.add(row);
        }
        return List.copyOf(segments);
    }

    private ProcessFlowTemperaturePointDto toTemperaturePoint(
            ProcessFlowRawRow row,
            Map<Integer, ProcessFlowOpstatMappingDto> mappingByCode
    ) {
        return new ProcessFlowTemperaturePointDto(
                row.prdtime().toString(),
                row.opstat(),
                labelFor(row.opstat(), mappingByCode),
                row.t1Pv(),
                row.t2Pv(),
                row.t1Sv(),
                row.t2Sv(),
                row.co2Pv(),
                row.coPv(),
                row.cpPv(),
                row.opalarm(),
                row.workorder(),
                row.patPgm(),
                row.segmentNo(),
                row.segmentTime(),
                row.segmentTotal()
        );
    }

    private ProcessFlowLatestDto toLatest(
            ProcessFlowRawRow row,
            Map<Integer, ProcessFlowOpstatMappingDto> mappingByCode
    ) {
        return new ProcessFlowLatestDto(
                row.prdtime().toString(),
                row.opstat(),
                labelFor(row.opstat(), mappingByCode),
                row.opalarm(),
                row.workorder(),
                row.patPgm(),
                row.segmentNo(),
                row.segmentTime(),
                row.segmentTotal(),
                row.t1Pv(),
                row.t2Pv(),
                row.t1Sv(),
                row.t2Sv(),
                row.co2Pv(),
                row.coPv(),
                row.cpPv()
        );
    }

    private ProcessFlowCurrentStateDto toCurrentState(
            SegmentAccumulator segment,
            ProcessFlowRawRow latestRow,
            Map<Integer, ProcessFlowOpstatMappingDto> mappingByCode
    ) {
        return new ProcessFlowCurrentStateDto(
                segment.opstat(),
                labelFor(segment.opstat(), mappingByCode),
                segment.start().toString(),
                durationSeconds(segment.start(), latestRow.prdtime()),
                segment.rowCount()
        );
    }

    private List<ProcessFlowSegmentDto> toSegmentDtos(
            List<SegmentAccumulator> segments,
            Map<Integer, ProcessFlowOpstatMappingDto> mappingByCode
    ) {
        List<ProcessFlowSegmentDto> result = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            SegmentAccumulator segment = segments.get(index);
            Instant end = resolveSegmentEnd(segments, index);
            result.add(new ProcessFlowSegmentDto(
                    segment.opstat(),
                    labelFor(segment.opstat(), mappingByCode),
                    segment.start().toString(),
                    end.toString(),
                    durationSeconds(segment.start(), end),
                    segment.rowCount()
            ));
        }
        return List.copyOf(result);
    }

    private List<ProcessFlowStageSummaryDto> toStageSummaryDtos(
            List<SegmentAccumulator> segments,
            Map<Integer, ProcessFlowOpstatMappingDto> mappingByCode
    ) {
        List<ProcessFlowStageSummaryDto> result = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            SegmentAccumulator segment = segments.get(index);
            Instant end = resolveSegmentEnd(segments, index);
            result.add(new ProcessFlowStageSummaryDto(
                    segment.opstat(),
                    labelFor(segment.opstat(), mappingByCode),
                    segment.start().toString(),
                    end.toString(),
                    durationSeconds(segment.start(), end),
                    segment.rowCount(),
                    segment.t1PvStats().avg(),
                    segment.t2PvStats().avg(),
                    segment.t1SvStats().avg(),
                    segment.t2SvStats().avg(),
                    segment.t1PvStats().min(),
                    segment.t1PvStats().max(),
                    segment.t2PvStats().min(),
                    segment.t2PvStats().max()
            ));
        }
        return List.copyOf(result);
    }

    private List<ProcessFlowEventDto> buildEventTimeline(
            List<SegmentAccumulator> segments,
            List<ProcessFlowRawRow> rows,
            Map<Integer, ProcessFlowOpstatMappingDto> mappingByCode,
            List<String> warnings
    ) {
        List<ProcessFlowEventDto> events = new ArrayList<>();
        for (int index = 1; index < segments.size(); index++) {
            SegmentAccumulator previous = segments.get(index - 1);
            SegmentAccumulator current = segments.get(index);
            events.add(new ProcessFlowEventDto(
                    current.start().toString(),
                    "STATE_CHANGE",
                    previous.opstat(),
                    labelFor(previous.opstat(), mappingByCode),
                    current.opstat(),
                    labelFor(current.opstat(), mappingByCode),
                    labelFor(previous.opstat(), mappingByCode)
                            + " -> "
                            + labelFor(current.opstat(), mappingByCode)
            ));
        }

        ProcessFlowRawRow previousRow = null;
        for (ProcessFlowRawRow row : rows) {
            if (previousRow != null && !Objects.equals(previousRow.opalarm(), row.opalarm())) {
                events.add(new ProcessFlowEventDto(
                        row.prdtime().toString(),
                        "ALARM_CHANGE",
                        null,
                        previousRow.opalarm(),
                        null,
                        row.opalarm(),
                        "OPALARM " + displayText(previousRow.opalarm()) + " -> " + displayText(row.opalarm())
                ));
            }
            if (previousRow != null && !Objects.equals(previousRow.workorder(), row.workorder())) {
                events.add(new ProcessFlowEventDto(
                        row.prdtime().toString(),
                        "WORKORDER_CHANGE",
                        null,
                        previousRow.workorder(),
                        null,
                        row.workorder(),
                        "WORKORDER " + displayText(previousRow.workorder()) + " -> " + displayText(row.workorder())
                ));
            }
            previousRow = row;
        }

        events.sort(Comparator.comparing(ProcessFlowEventDto::time));
        if (events.size() > MAX_EVENTS) {
            warnings.add("Event timeline was truncated to " + MAX_EVENTS + " events.");
            return List.copyOf(events.subList(0, MAX_EVENTS));
        }
        return List.copyOf(events);
    }

    private Instant resolveSegmentEnd(List<SegmentAccumulator> segments, int index) {
        if (index + 1 < segments.size()) {
            return segments.get(index + 1).start();
        }
        return segments.get(index).end();
    }

    private String labelFor(Integer opstat, Map<Integer, ProcessFlowOpstatMappingDto> mappingByCode) {
        if (opstat == null) {
            return "Unknown";
        }
        ProcessFlowOpstatMappingDto mapping = mappingByCode.get(opstat);
        return mapping == null ? "OPSTAT " + opstat : mapping.label();
    }

    private long durationSeconds(Instant start, Instant end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0L;
        }
        return Duration.between(start, end).toSeconds();
    }

    private String resolveMatchFilterMccode(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
                if (key == null || key.isBlank()) {
                    continue;
                }
                if ("MCCODE".equalsIgnoreCase(key)) {
                    String resolved = resolveMccodeFromFilterValue(entry.getValue());
                    if (resolved != null) {
                        return resolved;
                    }
                }
                String nested = resolveMatchFilterMccode(entry.getValue());
                if (nested != null) {
                    return nested;
                }
            }
        }
        if (value instanceof List<?> listValue) {
            for (Object item : listValue) {
                String nested = resolveMatchFilterMccode(item);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String resolveMccodeFromFilterValue(Object value) {
        if (value instanceof String textValue) {
            return normalizeMccode(textValue);
        }
        if (value instanceof Map<?, ?> mapValue) {
            Object eqValue = mapValue.get("$eq");
            if (eqValue instanceof String eqText) {
                return normalizeMccode(eqText);
            }
            Object inValue = mapValue.get("$in");
            if (inValue instanceof List<?> listValue) {
                for (Object candidate : listValue) {
                    if (candidate instanceof String candidateText) {
                        return normalizeMccode(candidateText);
                    }
                }
            }
        }
        return null;
    }

    private Instant parseRequiredInstant(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    fieldName + " must be a valid UTC ISO instant (e.g. 2026-04-01T00:00:00Z)."
            );
        }
    }

    private void validateRange(Instant start, Instant end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must be earlier than or equal to end.");
        }
    }

    private Object valueOf(Document document, String... names) {
        if (document == null || names == null) {
            return null;
        }
        for (String name : names) {
            if (name != null && document.containsKey(name)) {
                return document.get(name);
            }
        }
        for (String name : names) {
            if (name == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private Instant asInstant(Object value) {
        if (value instanceof Date dateValue) {
            return dateValue.toInstant();
        }
        if (value instanceof Instant instantValue) {
            return instantValue;
        }
        if (value instanceof String textValue) {
            String normalized = normalizeOptionalText(textValue);
            if (normalized == null) {
                return null;
            }
            try {
                return Instant.parse(normalized);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number numberValue) {
            double parsed = numberValue.doubleValue();
            return Double.isFinite(parsed) ? parsed : null;
        }
        if (value instanceof String textValue) {
            String normalized = normalizeOptionalText(textValue);
            if (normalized == null) {
                return null;
            }
            try {
                double parsed = Double.parseDouble(normalized);
                return Double.isFinite(parsed) ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String textValue) {
            String normalized = normalizeOptionalText(textValue);
            if (normalized == null) {
                return null;
            }
            try {
                return Integer.parseInt(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return normalizeOptionalText(String.valueOf(value));
    }

    private String normalizeMccode(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String displayText(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? "-" : normalized;
    }

    private record OpstatDefinition(int code, String label) {
    }

    private record ProcessFlowRawRow(
            Instant prdtime,
            Integer opstat,
            String opalarm,
            String workorder,
            String patPgm,
            Integer segmentNo,
            Integer segmentTime,
            Integer segmentTotal,
            Double t1Pv,
            Double t2Pv,
            Double t1Sv,
            Double t2Sv,
            Double co2Pv,
            Double coPv,
            Double cpPv
    ) {
    }

    private static final class SegmentAccumulator {
        private final Integer opstat;
        private final Instant start;
        private Instant end;
        private long rowCount;
        private final NumericStats t1PvStats = new NumericStats();
        private final NumericStats t2PvStats = new NumericStats();
        private final NumericStats t1SvStats = new NumericStats();
        private final NumericStats t2SvStats = new NumericStats();

        private SegmentAccumulator(Integer opstat, Instant start) {
            this.opstat = opstat;
            this.start = start;
            this.end = start;
        }

        private void add(ProcessFlowRawRow row) {
            rowCount++;
            end = row.prdtime();
            t1PvStats.accept(row.t1Pv());
            t2PvStats.accept(row.t2Pv());
            t1SvStats.accept(row.t1Sv());
            t2SvStats.accept(row.t2Sv());
        }

        private Integer opstat() {
            return opstat;
        }

        private Instant start() {
            return start;
        }

        private Instant end() {
            return end;
        }

        private long rowCount() {
            return rowCount;
        }

        private NumericStats t1PvStats() {
            return t1PvStats;
        }

        private NumericStats t2PvStats() {
            return t2PvStats;
        }

        private NumericStats t1SvStats() {
            return t1SvStats;
        }

        private NumericStats t2SvStats() {
            return t2SvStats;
        }
    }

    private static final class NumericStats {
        private long count;
        private double sum;
        private Double min;
        private Double max;

        private void accept(Double value) {
            if (value == null || !Double.isFinite(value)) {
                return;
            }
            count++;
            sum += value;
            min = min == null ? value : Math.min(min, value);
            max = max == null ? value : Math.max(max, value);
        }

        private Double avg() {
            return count <= 0L ? null : sum / count;
        }

        private Double min() {
            return min;
        }

        private Double max() {
            return max;
        }
    }
}
