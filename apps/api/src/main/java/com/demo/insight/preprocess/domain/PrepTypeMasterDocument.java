package com.demo.insight.preprocess.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "tmst_prep_mst")
public class PrepTypeMasterDocument {

    @Id
    private String id;

    private String prepTypeCd;
    private String prepTypeNm;
    private String desc;
    private Integer sortOrd;
    private String useYn;
    private Date createdAt;
    private Date updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPrepTypeCd() {
        return prepTypeCd;
    }

    public void setPrepTypeCd(String prepTypeCd) {
        this.prepTypeCd = prepTypeCd;
    }

    public String getPrepTypeNm() {
        return prepTypeNm;
    }

    public void setPrepTypeNm(String prepTypeNm) {
        this.prepTypeNm = prepTypeNm;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public Integer getSortOrd() {
        return sortOrd;
    }

    public void setSortOrd(Integer sortOrd) {
        this.sortOrd = sortOrd;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String useYn) {
        this.useYn = useYn;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}

