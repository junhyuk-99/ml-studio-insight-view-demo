package com.demo.insight.preprocess.dto;

import java.util.ArrayList;
import java.util.List;

public class DataSourceTypeDto {

    private final String typeCode;
    private final String typeName;
    private final Integer sortNo;
    private final List<DataSourceDetailDto> details = new ArrayList<>();

    public DataSourceTypeDto(String typeCode, String typeName, Integer sortNo) {
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.sortNo = sortNo;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getTypeName() {
        return typeName;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public List<DataSourceDetailDto> getDetails() {
        return details;
    }
}

