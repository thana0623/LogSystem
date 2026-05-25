package com.logsys.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private String code;
    private String message;
    private T details;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(null, null, data);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, T details) {
        return new ApiResponse<>(code, message, details);
    }
}
