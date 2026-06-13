package com.demo.insight.common.controller;

import com.demo.insight.common.dto.ApiResponse;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name:ml-studio-api}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> data = Map.of(
                "status", "UP",
                "application", applicationName,
                "profile", "public-demo",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.success(data, "API health check loaded."));
    }
}
