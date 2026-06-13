package com.demo.insight.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank(message = "empcode is required")
        String empcode,
        @NotBlank(message = "emppass is required")
        String emppass
) {
}

