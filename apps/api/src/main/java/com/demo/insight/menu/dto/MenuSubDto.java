package com.demo.insight.menu.dto;

import java.util.ArrayList;
import java.util.List;

public class MenuSubDto {

    private final String subcode;
    private final String subname;
    private final Integer sortno;
    private final List<MenuProgramDto> programs = new ArrayList<>();

    public MenuSubDto(String subcode, String subname, Integer sortno) {
        this.subcode = subcode;
        this.subname = subname;
        this.sortno = sortno;
    }

    public String getSubcode() {
        return subcode;
    }

    public String getSubname() {
        return subname;
    }

    public Integer getSortno() {
        return sortno;
    }

    public List<MenuProgramDto> getPrograms() {
        return programs;
    }
}

