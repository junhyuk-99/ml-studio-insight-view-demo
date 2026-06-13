package com.demo.insight.preprocess.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "tmst_prep_map")
public class PrepTypeMapDocument {

    @Id
    private String id;

    private String prepTypeCd;
    private String prepCd;
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

    public String getPrepCd() {
        return prepCd;
    }

    public void setPrepCd(String prepCd) {
        this.prepCd = prepCd;
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

