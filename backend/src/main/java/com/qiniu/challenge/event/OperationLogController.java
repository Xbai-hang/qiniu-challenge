package com.qiniu.challenge.event;

import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<OperationLogPage> listLogs(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(required = false) Long calendarSpaceId,
            @RequestParam(required = false) String operationSource,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(operationLogService.listLogs(
                principal.userId(),
                calendarSpaceId,
                operationSource,
                targetType,
                page,
                size));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportLogs(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(required = false) Long calendarSpaceId,
            @RequestParam(required = false) String operationSource,
            @RequestParam(required = false) String targetType) {
        byte[] csv = operationLogService.exportLogs(
                principal.userId(),
                calendarSpaceId,
                operationSource,
                targetType);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("operation-logs.csv", java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(csv);
    }
}
