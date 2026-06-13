package com.demo.insight.menu.dto;

import java.util.List;

public record MenuResponseDto(
        List<MenuMidDto> menus
) {
}

