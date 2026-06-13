package com.demo.insight.menu.service;

import com.demo.insight.menu.dto.MenuResponseDto;

public interface MenuService {
    MenuResponseDto getMenuByRole(String role);
}

