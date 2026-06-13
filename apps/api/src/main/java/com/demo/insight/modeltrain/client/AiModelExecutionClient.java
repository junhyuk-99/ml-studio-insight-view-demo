package com.demo.insight.modeltrain.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class AiModelExecutionClient {
    private static final Logger log = LoggerFactory.getLogger(AiModelExecutionClient.class);

    private static final String ALGO_ISOLATION_FOREST = "ISOLATION_FOREST";
    private static final String ALGO_AUTOENCODER = "AUTOENCODER";
    private static final String ALGO_RANDOM_FOREST = "RANDOM_FOREST";

    private final RestTemplate restTemplate;
    private final String isolationForestExecuteUrl;
    private final String autoEncoderExecuteUrl;
    private final String randomForestExecuteUrl;

    public AiModelExecutionClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.ai-server.base-url:http://localhost:8001}") String baseUrl,
            @Value("${app.ai-server.execute-path:${APP_AI_SERVER_EXECUTE_PATH:/api/model/execute/isolation-forest}}")
            String isolationForestExecutePath,
            @Value("${app.ai-server.autoencoder-execute-path:${APP_AI_SERVER_AUTOENCODER_EXECUTE_PATH:/api/model/execute/autoencoder}}")
            String autoEncoderExecutePath,
            @Value("${app.ai-server.random-forest-execute-path:${APP_AI_SERVER_RANDOM_FOREST_EXECUTE_PATH:/api/model/execute/random-forest}}")
            String randomForestExecutePath
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(180))
                .build();
        this.isolationForestExecuteUrl = joinUrl(baseUrl, isolationForestExecutePath, ALGO_ISOLATION_FOREST);
        this.autoEncoderExecuteUrl = joinUrl(baseUrl, autoEncoderExecutePath, ALGO_AUTOENCODER);
        this.randomForestExecuteUrl = joinUrl(baseUrl, randomForestExecutePath, ALGO_RANDOM_FOREST);

        log.info(
                "Configured AI execute URLs. isolation_forest_url={}, autoencoder_url={}, random_forest_url={}",
                isolationForestExecuteUrl,
                autoEncoderExecuteUrl,
                randomForestExecuteUrl
        );
    }

    public List<AiInferenceResult> executeIsolationForest(Map<String, Object> payload) {
        return execute(isolationForestExecuteUrl, payload);
    }

    public List<AiInferenceResult> executeAutoEncoder(Map<String, Object> payload) {
        return execute(autoEncoderExecuteUrl, payload);
    }

    public AiRandomForestExecutionResult executeRandomForest(Map<String, Object> payload) {
        try {
            ResponseEntity<AiRandomForestExecutionResult> response = restTemplate.postForEntity(
                    randomForestExecuteUrl,
                    payload,
                    AiRandomForestExecutionResult.class
            );
            AiRandomForestExecutionResult body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("AI server random-forest response body is empty.");
            }
            if (body.predictions() == null) {
                throw new IllegalStateException("AI server random-forest response must include predictions.");
            }
            return body;
        } catch (RestClientException exception) {
            throw toAiServerRequestException(ALGO_RANDOM_FOREST, randomForestExecuteUrl, exception);
        }
    }

    private List<AiInferenceResult> execute(String executeUrl, Map<String, Object> payload) {
        Object responseBody;
        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(executeUrl, payload, Object.class);
            responseBody = response.getBody();
        } catch (RestClientException exception) {
            throw toAiServerRequestException("INFERENCE", executeUrl, exception);
        }

        List<?> rawRows = extractResultRows(responseBody);
        List<AiInferenceResult> results = new ArrayList<>(rawRows.size());

        for (Object rawRow : rawRows) {
            if (!(rawRow instanceof Map<?, ?> rowMap)) {
                throw new IllegalStateException("AI server response row must be an object.");
            }

            Double anomalyScore = toDouble(
                    firstNonNull(
                            rowMap.get("anomaly_score"),
                            rowMap.get("anomalyScore"),
                            rowMap.get("score")
                    )
            );
            Boolean isAnomaly = toBoolean(
                    firstNonNull(
                            rowMap.get("is_anomaly"),
                            rowMap.get("isAnomaly"),
                            rowMap.get("anomaly"),
                            rowMap.get("label"),
                            rowMap.get("prediction")
                    )
            );
            Double healthIndex = toDouble(
                    firstNonNull(
                            rowMap.get("health_index"),
                            rowMap.get("healthIndex"),
                            rowMap.get("health")
                    )
            );
            String status = toText(
                    firstNonNull(
                            rowMap.get("status"),
                            rowMap.get("health_status")
                    )
            );

            if (anomalyScore == null || isAnomaly == null) {
                throw new IllegalStateException("AI server response row must include anomaly_score and is_anomaly.");
            }

            results.add(new AiInferenceResult(anomalyScore, isAnomaly, healthIndex, status));
        }

        return results;
    }

    public String getExecuteUrl() {
        return isolationForestExecuteUrl;
    }

    public String getAutoEncoderExecuteUrl() {
        return autoEncoderExecuteUrl;
    }

    public String getRandomForestExecuteUrl() {
        return randomForestExecuteUrl;
    }

    private List<?> extractResultRows(Object responseBody) {
        if (responseBody instanceof List<?> rootList) {
            return rootList;
        }

        if (!(responseBody instanceof Map<?, ?> rootMap)) {
            throw new IllegalStateException("AI server response must be an object or list.");
        }

        Object resultRows = firstNonNull(
                rootMap.get("results"),
                rootMap.get("anomaly_results"),
                rootMap.get("rows")
        );

        if (resultRows == null && rootMap.get("data") instanceof Map<?, ?> dataMap) {
            resultRows = firstNonNull(
                    dataMap.get("results"),
                    dataMap.get("anomaly_results"),
                    dataMap.get("rows")
            );

            if (resultRows == null) {
                List<Map<String, Object>> composedRows = composeRowsFromArrays(
                        firstNonNull(dataMap.get("anomaly_scores"), dataMap.get("scores")),
                        firstNonNull(dataMap.get("is_anomaly"), dataMap.get("labels"), dataMap.get("predictions"))
                );
                if (composedRows != null) {
                    return composedRows;
                }
            }
        }

        if (resultRows == null) {
            List<Map<String, Object>> composedRows = composeRowsFromArrays(
                    firstNonNull(rootMap.get("anomaly_scores"), rootMap.get("scores")),
                    firstNonNull(rootMap.get("is_anomaly"), rootMap.get("labels"), rootMap.get("predictions"))
            );
            if (composedRows != null) {
                return composedRows;
            }
        }

        if (!(resultRows instanceof List<?> resultList)) {
            throw new IllegalStateException("AI server response must include results list.");
        }

        return resultList;
    }

    private List<Map<String, Object>> composeRowsFromArrays(Object rawScores, Object rawLabels) {
        if (!(rawScores instanceof List<?> scores) || !(rawLabels instanceof List<?> labels)) {
            return null;
        }
        if (scores.size() != labels.size()) {
            throw new IllegalStateException("AI server score/label array lengths do not match.");
        }

        List<Map<String, Object>> rows = new ArrayList<>(scores.size());
        for (int index = 0; index < scores.size(); index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("anomaly_score", scores.get(index));
            row.put("is_anomaly", labels.get(index));
            rows.add(row);
        }
        return rows;
    }

    private String joinUrl(String baseUrl, String path, String algorithm) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.trim();
        String normalizedPath = path == null ? "" : path.trim();

        if (normalizedBase.isEmpty()) {
            throw new IllegalStateException("app.ai-server.base-url must not be empty.");
        }
        if (normalizedPath.isEmpty()) {
            throw new IllegalStateException("AI server execute path must not be empty. algorithm=" + algorithm);
        }

        boolean baseEndsWithSlash = normalizedBase.endsWith("/");
        boolean pathStartsWithSlash = normalizedPath.startsWith("/");

        if (baseEndsWithSlash && pathStartsWithSlash) {
            return normalizedBase + normalizedPath.substring(1);
        }
        if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return normalizedBase + "/" + normalizedPath;
        }
        return normalizedBase + normalizedPath;
    }

    private IllegalStateException toAiServerRequestException(String algorithm, String executeUrl, RestClientException exception) {
        StringBuilder messageBuilder = new StringBuilder("AI server request failed. algorithm=")
                .append(algorithm)
                .append(", url=")
                .append(executeUrl);

        if (exception instanceof RestClientResponseException responseException) {
            int statusCode = responseException.getRawStatusCode();
            messageBuilder.append(", status=").append(statusCode);
            String responseBody = normalizeResponseBody(responseException.getResponseBodyAsString());
            if (responseBody != null) {
                messageBuilder.append(", response=").append(responseBody);
            }
            if (ALGO_RANDOM_FOREST.equals(algorithm) && statusCode == 404) {
                messageBuilder.append(", hint=AI ?혵甕걔?혨 Random Forest endpoint揶쎛 ?源??혱???혞筌왖 ?혡???떷?? expected_endpoint=/api/model/execute/random-forest");
            }
        } else {
            String exceptionMessage = toText(exception.getMessage());
            if (exceptionMessage != null) {
                messageBuilder.append(", reason=").append(exceptionMessage);
            }
        }

        return new IllegalStateException(messageBuilder.toString(), exception);
    }

    private String normalizeResponseBody(String responseBody) {
        String normalized = toText(responseBody);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= 1000) {
            return normalized;
        }
        return normalized.substring(0, 1000) + "...(truncated)";
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "y".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "n".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        return null;
    }
}
