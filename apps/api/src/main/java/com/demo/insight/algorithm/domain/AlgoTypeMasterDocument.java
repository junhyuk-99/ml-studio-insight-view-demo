package com.demo.insight.algorithm.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "tmst_algo_mst")
public class AlgoTypeMasterDocument {

    @Id
    private String id;

    private String algoTypeCd;
    private String algoTypeNm;
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

    public String getAlgoTypeCd() {
        return algoTypeCd;
    }

    public void setAlgoTypeCd(String algoTypeCd) {
        this.algoTypeCd = algoTypeCd;
    }

    public String getAlgoTypeNm() {
        return algoTypeNm;
    }

    public void setAlgoTypeNm(String algoTypeNm) {
        this.algoTypeNm = algoTypeNm;
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
