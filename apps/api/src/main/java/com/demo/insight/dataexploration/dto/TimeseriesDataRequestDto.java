package com.demo.insight.dataexploration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TimeseriesDataRequestDto(
        @JsonProperty("dataset_key")
        @JsonAlias("datasetKey")
        String datasetKey,

        @JsonProperty("equipment_id")
        @JsonAlias("equipmentId")
        String equipmentId,

        @NotBlank(message = "from is required.")
        String from,

        @NotBlank(message = "to is required.")
        String to,

        @JsonProperty("selected_fields")
        @NotEmpty(message = "selected_fields must not be empty.")
        List<String> selectedFields,

        @JsonProperty("max_points")
        Integer maxPoints
) {
}
