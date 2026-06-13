package com.demo.insight.equipment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EquipmentMasterDto(
        @JsonProperty("MCCODE")
        String mccode,

        @JsonProperty("MCNAME")
        String mcname,

        @JsonProperty("process_type")
        String processType,

        @JsonProperty("opstat_code_group")
        String opstatCodeGroup,

        @JsonProperty("ai_use_flag")
        String aiUseFlag,

        @JsonProperty("dataset_key")
        String datasetKey
) {
}
