package com.demo.insight.auth.dto;

public record LoginResponseDto(
        String empcode,
        String empname,
        String role,
        String useflag
) {
}

