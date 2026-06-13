package com.demo.insight.menu.dto;

import java.util.ArrayList;
import java.util.List;

public class MenuMidDto {

    private final String midcode;
    private final String midname;
    private final Integer sortno;
    private final List<MenuProgramDto> programs = new ArrayList<>();
    private final List<MenuSubDto> submenus = new ArrayList<>();

    public MenuMidDto(String midcode, String midname, Integer sortno) {
        this.midcode = midcode;
        this.midname = midname;
        this.sortno = sortno;
    }

    public String getMidcode() {
        return midcode;
    }

    public String getMidname() {
        return midname;
    }

    public Integer getSortno() {
        return sortno;
    }

    public List<MenuProgramDto> getPrograms() {
        return programs;
    }

    public List<MenuSubDto> getSubmenus() {
        return submenus;
    }
}

