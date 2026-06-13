package com.demo.insight.preprocess.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Document(collection = "tmst_data_type")
public class DataTypeDatasetDocument {

    @Id
    private String id;

    @Field("DATASET_KEY")
    private String datasetKey;

    @Field("DATASET_NAME")
    private String datasetName;

    @Field("DISPLAY_NAME")
    private String displayName;

    @Field("TYPE_CODE")
    private String typeCode;

    @Field("DTL_CODE")
    private String dtlCode;

    @Field("SOURCE_COLLECTION")
    private String sourceCollection;

    @Field("TARGET_FEATURE_COLLECTION")
    private String targetFeatureCollection;

    @Field("LABEL_FIELD")
    private String labelField;

    @Field("DATASET_PURPOSE")
    private String datasetPurpose;

    @Field("FEATURE_ENABLED")
    private Boolean featureEnabled;

    @Field("EQUIPMENT_GROUP")
    private String equipmentGroup;

    @Field("EQUIPMENT_GROUP_NAME")
    private String equipmentGroupName;

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

    public String getDatasetKey() {
        return datasetKey;
    }

    public void setDatasetKey(String datasetKey) {
        this.datasetKey = datasetKey;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public String getSourceCollection() {
        return sourceCollection;
    }

    public void setSourceCollection(String sourceCollection) {
        this.sourceCollection = sourceCollection;
    }

    public String getTargetFeatureCollection() {
        return targetFeatureCollection;
    }

    public void setTargetFeatureCollection(String targetFeatureCollection) {
        this.targetFeatureCollection = targetFeatureCollection;
    }

    public String getLabelField() {
        return labelField;
    }

    public void setLabelField(String labelField) {
        this.labelField = labelField;
    }

    public String getDatasetPurpose() {
        return datasetPurpose;
    }

    public void setDatasetPurpose(String datasetPurpose) {
        this.datasetPurpose = datasetPurpose;
    }

    public Boolean getFeatureEnabled() {
        return featureEnabled;
    }

    public void setFeatureEnabled(Boolean featureEnabled) {
        this.featureEnabled = featureEnabled;
    }

    public String getEquipmentGroup() {
        return equipmentGroup;
    }

    public void setEquipmentGroup(String equipmentGroup) {
        this.equipmentGroup = equipmentGroup;
    }

    public String getEquipmentGroupName() {
        return equipmentGroupName;
    }

    public void setEquipmentGroupName(String equipmentGroupName) {
        this.equipmentGroupName = equipmentGroupName;
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