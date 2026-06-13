package com.demo.insight.preprocess.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "tmst_prep_dtl")
public class PrepOptionDetailDocument {

    @Id
    private String id;

    private String prepCd;
    private String prepNm;
    private String useYn;
    private Date createdAt;
    private Date updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPrepCd() {
        return prepCd;
    }

    public void setPrepCd(String prepCd) {
        this.prepCd = prepCd;
    }

    public String getPrepNm() {
        return prepNm;
    }

    public void setPrepNm(String prepNm) {
        this.prepNm = prepNm;
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

