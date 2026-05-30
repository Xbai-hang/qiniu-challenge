package com.qiniu.challenge.event;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final OperationLogRepository operationLogRepository;
    private final PermissionService permissionService;

    public OperationLogService(
            OperationLogRepository operationLogRepository,
            PermissionService permissionService) {
        this.operationLogRepository = operationLogRepository;
        this.permissionService = permissionService;
    }

    public OperationLogPage listLogs(
            long currentUserId,
            Long calendarSpaceId,
            String operationSource,
            String targetType,
            Integer page,
            Integer size) {
        if (calendarSpaceId != null) {
            CalendarSpaceAccess space = permissionService.requireSpaceAccess(calendarSpaceId, currentUserId);
            permissionService.requireCanViewOperationLogs(space, currentUserId);
        }
        return operationLogRepository.findLogs(query(
                currentUserId,
                calendarSpaceId,
                operationSource,
                targetType,
                page,
                size));
    }

    public byte[] exportLogs(
            long currentUserId,
            Long calendarSpaceId,
            String operationSource,
            String targetType) {
        if (calendarSpaceId != null) {
            CalendarSpaceAccess space = permissionService.requireSpaceAccess(calendarSpaceId, currentUserId);
            permissionService.requireCanViewOperationLogs(space, currentUserId);
        }
        List<OperationLogRecord> logs = operationLogRepository.findLogsForExport(query(
                currentUserId,
                calendarSpaceId,
                operationSource,
                targetType,
                1,
                5000));
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff');
        csv.append("id,createdAt,userId,userDisplayName,calendarSpaceId,calendarSpaceName,operationSource,operationType,targetType,targetId,undoable,undone,undoExpiresAt,beforeSnapshot,afterSnapshot\n");
        for (OperationLogRecord log : logs) {
            appendRow(csv, log);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private OperationLogQuery query(
            long currentUserId,
            Long calendarSpaceId,
            String operationSource,
            String targetType,
            Integer page,
            Integer size) {
        int normalizedPage = page == null ? 1 : Math.max(1, page);
        int normalizedSize = size == null ? 20 : Math.max(1, Math.min(size, 100));
        return new OperationLogQuery(
                currentUserId,
                calendarSpaceId,
                blankToNull(operationSource),
                blankToNull(targetType),
                normalizedPage,
                normalizedSize);
    }

    private void appendRow(StringBuilder csv, OperationLogRecord log) {
        csv.append(log.id()).append(',')
                .append(csv(log.createdAt() == null ? "" : DATE_TIME_FORMATTER.format(log.createdAt()))).append(',')
                .append(log.userId()).append(',')
                .append(csv(log.userDisplayName())).append(',')
                .append(log.calendarSpaceId()).append(',')
                .append(csv(log.calendarSpaceName())).append(',')
                .append(csv(log.operationSource())).append(',')
                .append(csv(log.operationType())).append(',')
                .append(csv(log.targetType())).append(',')
                .append(log.targetId() == null ? "" : log.targetId()).append(',')
                .append(log.undoable()).append(',')
                .append(log.undone()).append(',')
                .append(csv(log.undoExpiresAt() == null ? "" : DATE_TIME_FORMATTER.format(log.undoExpiresAt()))).append(',')
                .append(csv(log.beforeSnapshot())).append(',')
                .append(csv(log.afterSnapshot()))
                .append('\n');
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
