package com.demo.insight.dataexploration.repository;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Repository
public class DataExplorationRepository {

    private static final String DEFAULT_TIMESTAMP_FIELD = "PRDTIME";

    private final MongoTemplate mongoTemplate;

    public DataExplorationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public TimestampRange findTimestampRange(String sourceCollection, Document additionalMatchFilter) {
        Document timestampTypeFilter = new Document(DEFAULT_TIMESTAMP_FIELD, new Document("$type", "date"));
        Document matchFilter = mergeWithAnd(timestampTypeFilter, additionalMatchFilter);

        List<Document> pipeline = List.of(
                new Document("$match", matchFilter),
                new Document("$group", new Document("_id", null)
                        .append("min_timestamp", new Document("$min", "$" + DEFAULT_TIMESTAMP_FIELD))
                        .append("max_timestamp", new Document("$max", "$" + DEFAULT_TIMESTAMP_FIELD)))
        );

        Document result = mongoTemplate.getCollection(sourceCollection).aggregate(pipeline).first();
        if (result == null) {
            return null;
        }

        Date minTimestamp = asDate(result.get("min_timestamp"));
        Date maxTimestamp = asDate(result.get("max_timestamp"));
        if (minTimestamp == null || maxTimestamp == null) {
            return null;
        }

        return new TimestampRange(minTimestamp, maxTimestamp);
    }

    public long countRowsByTimestampRange(
            String sourceCollection,
            Date fromInclusive,
            Date toInclusive,
            Document additionalMatchFilter
    ) {
        Document matchFilter = buildTimestampRangeMatch(fromInclusive, toInclusive, additionalMatchFilter);
        return mongoTemplate.getCollection(sourceCollection).countDocuments(matchFilter);
    }

    public List<Document> findSampleRowsByTimestampRange(
            String sourceCollection,
            Date fromInclusive,
            Date toInclusive,
            int limit,
            Document additionalMatchFilter
    ) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", buildTimestampRangeMatch(fromInclusive, toInclusive, additionalMatchFilter)));
        pipeline.add(new Document("$sort", new Document(DEFAULT_TIMESTAMP_FIELD, -1).append("_id", -1)));
        pipeline.add(new Document("$limit", Math.max(limit, 1)));
        pipeline.add(new Document("$project", new Document("_id", 0)));

        List<Document> rows = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection(sourceCollection).aggregate(pipeline).allowDiskUse(true)) {
            rows.add(row);
        }
        return rows;
    }

    public List<Document> findSampledRowsByTimestampRange(
            String sourceCollection,
            Date fromInclusive,
            Date toInclusive,
            List<String> fieldNames,
            long samplingStep,
            int limit,
            String groupByField,
            Document additionalMatchFilter
    ) {
        String timestampField = DEFAULT_TIMESTAMP_FIELD;
        List<String> normalizedFieldNames = normalizeFieldNames(fieldNames);
        String normalizedGroupByField = normalizeFieldName(groupByField);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", buildTimestampRangeMatch(fromInclusive, toInclusive, additionalMatchFilter)));

        Document stableSort = new Document(timestampField, 1).append("_id", 1);
        Document windowSort = new Document(timestampField, 1);

        pipeline.add(new Document("$sort", stableSort));
        pipeline.add(new Document("$setWindowFields", new Document("sortBy", windowSort)
                .append("output", new Document("rowNumber", new Document("$documentNumber", new Document())))));

        if (samplingStep > 1L) {
            Document rowMod = new Document("$mod", List.of(
                    new Document("$subtract", List.of("$rowNumber", 1)),
                    samplingStep
            ));
            pipeline.add(new Document("$match", new Document("$expr", new Document("$eq", List.of(rowMod, 0)))));
        }

        pipeline.add(new Document("$limit", Math.max(limit, 1)));

        Document projection = new Document("_id", 0).append(timestampField, 1);
        for (String fieldName : normalizedFieldNames) {
            if (normalizedGroupByField != null && fieldName.equalsIgnoreCase(normalizedGroupByField)) {
                continue;
            }
            projection.append(fieldName, new Document("$convert", new Document("input", "$" + fieldName)
                    .append("to", "double")
                    .append("onError", null)
                    .append("onNull", null)));
        }

        if (normalizedGroupByField != null) {
            projection.append(normalizedGroupByField, new Document("$ifNull", buildBsonArray("$" + normalizedGroupByField, null)));
        }
        pipeline.add(new Document("$project", projection));

        List<Document> sampledRows = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection(sourceCollection)
                .aggregate(pipeline)
                .allowDiskUse(true)) {
            sampledRows.add(row);
        }
        return sampledRows;
    }

    public HistogramFieldStats aggregateFieldStats(
            String sourceCollection,
            Date fromInclusive,
            Date toInclusive,
            String fieldName,
            Document additionalMatchFilter
    ) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", buildTimestampRangeMatch(fromInclusive, toInclusive, additionalMatchFilter)));
        pipeline.add(buildNumericProjectionStage(fieldName));
        pipeline.add(new Document("$match", new Document("value", new Document("$ne", null))));
        pipeline.add(new Document("$group", new Document("_id", null)
                .append("count", new Document("$sum", 1))
                .append("min", new Document("$min", "$value"))
                .append("max", new Document("$max", "$value"))
                .append("avg", new Document("$avg", "$value"))));

        Document grouped = mongoTemplate.getCollection(sourceCollection)
                .aggregate(pipeline)
                .allowDiskUse(true)
                .first();

        if (grouped == null) {
            return new HistogramFieldStats(0L, null, null, null);
        }

        return new HistogramFieldStats(
                asLong(grouped.get("count")),
                asDouble(grouped.get("min")),
                asDouble(grouped.get("max")),
                asDouble(grouped.get("avg"))
        );
    }

    public List<HistogramBucketCount> aggregateBucketCounts(
            String sourceCollection,
            Date fromInclusive,
            Date toInclusive,
            String fieldName,
            List<Double> boundaries,
            Document additionalMatchFilter
    ) {
        if (boundaries == null || boundaries.size() < 2) {
            return List.of();
        }

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", buildTimestampRangeMatch(fromInclusive, toInclusive, additionalMatchFilter)));
        pipeline.add(buildNumericProjectionStage(fieldName));
        pipeline.add(new Document("$match", new Document("value", new Document("$ne", null))));
        pipeline.add(new Document("$bucket", new Document("groupBy", "$value")
                .append("boundaries", boundaries)
                .append("default", "__out_of_range__")
                .append("output", new Document("count", new Document("$sum", 1)))));

        List<HistogramBucketCount> counts = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection(sourceCollection)
                .aggregate(pipeline)
                .allowDiskUse(true)) {
            Object bucketId = row.get("_id");
            if (!(bucketId instanceof Number numberValue)) {
                continue;
            }
            counts.add(new HistogramBucketCount(numberValue.doubleValue(), asLong(row.get("count"))));
        }
        return counts;
    }

    private Document buildTimestampRangeMatch(Date fromInclusive, Date toInclusive, Document additionalMatchFilter) {
        Document baseRange = new Document(DEFAULT_TIMESTAMP_FIELD, new Document("$gte", fromInclusive).append("$lte", toInclusive));
        return mergeWithAnd(baseRange, additionalMatchFilter);
    }

    private Document mergeWithAnd(Document baseFilter, Document additionalMatchFilter) {
        Document clonedBase = cloneDocument(baseFilter);
        Document clonedAdditional = cloneDocument(additionalMatchFilter);

        if (clonedAdditional == null || clonedAdditional.isEmpty()) {
            return clonedBase == null ? new Document() : clonedBase;
        }
        if (clonedBase == null || clonedBase.isEmpty()) {
            return clonedAdditional;
        }

        return new Document("$and", List.of(clonedBase, clonedAdditional));
    }

    private List<String> normalizeFieldNames(List<String> fieldNames) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String fieldName : fieldNames) {
            String normalizedFieldName = normalizeFieldName(fieldName);
            if (normalizedFieldName == null) {
                continue;
            }
            normalized.add(normalizedFieldName);
        }
        return List.copyOf(normalized);
    }

    private String normalizeFieldName(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        String trimmed = fieldName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<Object> buildBsonArray(Object... values) {
        List<Object> bsonValues = new ArrayList<>();
        if (values == null || values.length == 0) {
            return bsonValues;
        }

        for (Object value : values) {
            if (value instanceof String textValue) {
                String normalized = textValue.trim();
                if (normalized.isEmpty()) {
                    continue;
                }
                bsonValues.add(normalized);
                continue;
            }
            bsonValues.add(value);
        }
        return bsonValues;
    }

    private Document buildNumericProjectionStage(String fieldName) {
        Document conversionExpression = new Document("$convert", new Document("input", "$" + fieldName)
                .append("to", "double")
                .append("onError", null)
                .append("onNull", null));
        return new Document("$project", new Document("value", conversionExpression));
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

    private Date asDate(Object value) {
        if (value instanceof Date dateValue) {
            return dateValue;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            try {
                return Date.from(Instant.parse(normalized));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
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

    private long asLong(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return 0L;
            }
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public record TimestampRange(Date minTimestamp, Date maxTimestamp) {
    }

    public record HistogramFieldStats(long sampleCount, Double min, Double max, Double avg) {
    }

    public record HistogramBucketCount(double bucketStart, long count) {
    }
}
