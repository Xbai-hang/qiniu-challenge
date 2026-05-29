package com.qiniu.challenge.common;

import java.util.UUID;

public record ApiResponse<T>(
        boolean success,
        T data,
        String requestId
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, UUID.randomUUID().toString());
    }
}
