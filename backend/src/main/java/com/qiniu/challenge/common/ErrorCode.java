package com.qiniu.challenge.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或 token 无效", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "权限不足", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT("CONFLICT", "业务冲突", HttpStatus.CONFLICT),
    CONFIRMATION_REQUIRED("CONFIRMATION_REQUIRED", "需要确认", HttpStatus.CONFLICT),
    AI_SERVICE_UNAVAILABLE("AI_SERVICE_UNAVAILABLE", "AI 服务不可用", HttpStatus.SERVICE_UNAVAILABLE),
    SPEECH_SERVICE_UNAVAILABLE("SPEECH_SERVICE_UNAVAILABLE", "语音识别不可用", HttpStatus.SERVICE_UNAVAILABLE),
    TTS_SERVICE_UNAVAILABLE("TTS_SERVICE_UNAVAILABLE", "TTS 不可用", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
