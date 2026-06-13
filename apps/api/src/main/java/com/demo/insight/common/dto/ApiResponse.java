package com.demo.insight.common.dto;

public record ApiResponse<T>(
        boolean ok,
        T data,
        String message,
        String errorCode
) {
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static <T> ApiResponse<T> failure(String message, String errorCode) {
        return new ApiResponse<>(false, null, message, errorCode);
    }
}

