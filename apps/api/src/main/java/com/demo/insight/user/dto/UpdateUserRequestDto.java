package com.demo.insight.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequestDto(
        @NotBlank(message = "empname is required")
        String empname,
        @NotBlank(message = "role is required")
        String role,
        @NotBlank(message = "useflag is required")
        String useflag
) {
}
