package com.demo.insight.user.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequestDto(
        @NotBlank(message = "empcode is required")
        String empcode,
        @NotBlank(message = "empname is required")
        String empname,
        @NotBlank(message = "emppass is required")
        String emppass,
        @NotBlank(message = "role is required")
        String role,
        @NotBlank(message = "useflag is required")
        String useflag
) {
}
