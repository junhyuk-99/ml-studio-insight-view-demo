package com.demo.insight.preprocess.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Document(collection = "tmst_data_type_dtl")
public class DataTypeDetailDocument {

    @Id
    private String id;

    @Field("TYPE_CODE")
    private String typeCode;

    @Field("DTL_CODE")
    private String dtlCode;

    @Field("DTL_NAME")
    private String dtlName;

    @Field("USE_FLAG")
    private String useFlag;

    @Field("SORT_NO")
    private Integer sortNo;

    @Field("REG_DATE")
    private Date regDate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getDtlCode() {
        return dtlCode;
    }

    public void setDtlCode(String dtlCode) {
        this.dtlCode = dtlCode;
    }

    public String getDtlName() {
        return dtlName;
    }

    public void setDtlName(String dtlName) {
        this.dtlName = dtlName;
    }

    public String getUseFlag() {
        return useFlag;
    }

    public void setUseFlag(String useFlag) {
        this.useFlag = useFlag;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Date getRegDate() {
        return regDate;
    }

    public void setRegDate(Date regDate) {
        this.regDate = regDate;
    }
}

