package com.demo.insight.menu.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tmstmenu_pgm")
public class MenuProgramDocument {

    @Id
    private String id;

    private String midcode;
    private String subcode;
    private String pgmcode;
    private String pgmname;
    private String pgmpath;
    private Integer sortno;
    private String useflag;
    private String role;

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

    public String getSubcode() {
        return subcode;
    }

    public void setSubcode(String subcode) {
        this.subcode = subcode;
    }

    public String getPgmcode() {
        return pgmcode;
    }

    public void setPgmcode(String pgmcode) {
        this.pgmcode = pgmcode;
    }

    public String getPgmname() {
        return pgmname;
    }

    public void setPgmname(String pgmname) {
        this.pgmname = pgmname;
    }

    public String getPgmpath() {
        return pgmpath;
    }

    public void setPgmpath(String pgmpath) {
        this.pgmpath = pgmpath;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}

