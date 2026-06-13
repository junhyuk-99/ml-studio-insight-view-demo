package com.demo.insight.home.controller;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.home.dto.HomeDashboardResponseDto;
import com.demo.insight.home.service.HomeDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeDashboardController {

    private final HomeDashboardService homeDashboardService;

    public HomeDashboardController(HomeDashboardService homeDashboardService) {
        this.homeDashboardService = homeDashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<HomeDashboardResponseDto>> getDashboard() {
        HomeDashboardResponseDto data = homeDashboardService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success(data, "Home dashboard loaded."));
    }
}
