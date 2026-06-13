package com.demo.insight.algorithm.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "tmst_algo_dtl")
public class AlgoDetailDocument {

    @Id
    private String id;

    private String algoCd;
    private String algoNm;
    private String useYn;
    private Date createdAt;
    private Date updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlgoCd() {
        return algoCd;
    }

    public void setAlgoCd(String algoCd) {
        this.algoCd = algoCd;
    }

    public String getAlgoNm() {
        return algoNm;
    }

    public void setAlgoNm(String algoNm) {
        this.algoNm = algoNm;
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
