package com.demo.insight.equipment.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "TMSTMC")
public class EquipmentMasterDocument {

    @Id
    private String id;

    @Field("MCCODE")
    private String mccode;

    @Field("MCNAME")
    private String mcname;

    @Field("USEFLAG")
    private String useFlag;

    @Field("process_type")
    private String processType;

    @Field("opstat_code_group")
    private String opstatCodeGroup;

    @Field("ai_use_flag")
    private String aiUseFlag;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMccode() {
        return mccode;
    }

    public void setMccode(String mccode) {
        this.mccode = mccode;
    }

    public String getMcname() {
        return mcname;
    }

    public void setMcname(String mcname) {
        this.mcname = mcname;
    }

    public String getUseFlag() {
        return useFlag;
    }

    public void setUseFlag(String useFlag) {
        this.useFlag = useFlag;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public String getOpstatCodeGroup() {
        return opstatCodeGroup;
    }

    public void setOpstatCodeGroup(String opstatCodeGroup) {
        this.opstatCodeGroup = opstatCodeGroup;
    }

    public String getAiUseFlag() {
        return aiUseFlag;
    }

    public void setAiUseFlag(String aiUseFlag) {
        this.aiUseFlag = aiUseFlag;
    }
}
