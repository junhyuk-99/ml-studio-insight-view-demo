package com.demo.insight.menu.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tmstmenu_mid")
public class MenuMidDocument {

    @Id
    private String id;

    private String midcode;
    private String midname;
    private Integer sortno;
    private String useflag;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMidcode() {
        return midcode;
    }

    public void setMidcode(String midcode) {
        this.midcode = midcode;
    }

    public String getMidname() {
        return midname;
    }

    public void setMidname(String midname) {
        this.midname = midname;
    }

    public Integer getSortno() {
        return sortno;
    }

    public void setSortno(Integer sortno) {
        this.sortno = sortno;
    }

    public String getUseflag() {
        return useflag;
    }

    public void setUseflag(String useflag) {
        this.useflag = useflag;
    }
}

