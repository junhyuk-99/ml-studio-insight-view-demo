package com.demo.insight.preprocess.dto;

import java.util.ArrayList;
import java.util.List;

public class DataSourceDetailDto {

    private final String dtlCode;
    private final String dtlName;
    private final Integer sortNo;
    private final List<DataSourceDatasetDto> datasets = new ArrayList<>();

    public DataSourceDetailDto(String dtlCode, String dtlName, Integer sortNo) {
        this.dtlCode = dtlCode;
        this.dtlName = dtlName;
        this.sortNo = sortNo;
    }

    public String getDtlCode() {
        return dtlCode;
    }

    public String getDtlName() {
        return dtlName;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public List<DataSourceDatasetDto> getDatasets() {
        return datasets;
    }
}
