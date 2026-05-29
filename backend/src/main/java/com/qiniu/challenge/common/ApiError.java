package com.qiniu.challenge.common;

import java.util.Map;

public record ApiError(
        String code,
        String message,
        Map<String, Object> details
) {
    public static ApiError of(ErrorCode code) {
        return of(code, code.defaultMessage(), Map.of());
    }

    public static ApiError of(ErrorCode code, String message) {
        return of(code, message, Map.of());
    }

    public static ApiError of(ErrorCode code, String message, Map<String, Object> details) {
        return new ApiError(code.code(), message, details == null ? Map.of() : details);
    }
}
