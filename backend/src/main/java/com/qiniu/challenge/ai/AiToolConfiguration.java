package com.qiniu.challenge.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import com.qiniu.challenge.event.ConflictCheckRequest;
import com.qiniu.challenge.event.EventCreateRequest;
import com.qiniu.challenge.event.EventService;
import com.qiniu.challenge.event.EventUpdateRequest;
import com.qiniu.challenge.reminder.CreateReminderRequest;
import com.qiniu.challenge.reminder.ReminderService;
import com.qiniu.challenge.reminder.SnoozeReminderRequest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiToolConfiguration {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    RegisteredTool listEventsTool(EventService eventService) {
        ToolDefinition definition = new ToolDefinition(
                "list_events",
                "查询当前用户可见的日历事件",
                schema("calendarSpaceId", "start", "end", "keyword", "project", "ownerUserId", "status", "priority"),
                RiskLevel.LOW,
                "event:read");
        return new RegisteredTool(definition, (context, arguments) -> ToolExecutionResult.succeeded(
                definition.name(),
                definition.baseRiskLevel(),
                eventService.listEvents(
                        context.userId(),
                        context.calendarSpaceId(),
                        offsetDateTime(arguments.get("start")),
                        offsetDateTime(arguments.get("end")),
                        stringValue(arguments.get("keyword")),
                        stringValue(arguments.get("project")),
                        longValue(arguments.get("ownerUserId")),
                        stringValue(arguments.get("status")),
                        stringValue(arguments.get("priority")),
                        stringValue(arguments.get("tag")),
                        stringValue(arguments.get("sortBy")),
                        stringValue(arguments.get("sortDirection")))));
    }

    @Bean
    RegisteredTool searchEventsTool(EventService eventService) {
        ToolDefinition definition = new ToolDefinition(
                "search_events",
                "按关键词搜索当前用户可见的日历事件",
                schema("calendarSpaceId", "keyword", "limit"),
                RiskLevel.LOW,
                "event:read");
        return new RegisteredTool(definition, (context, arguments) -> ToolExecutionResult.succeeded(
                definition.name(),
                definition.baseRiskLevel(),
                eventService.searchEvents(
                        context.userId(),
                        context.calendarSpaceId(),
                        stringValue(arguments.get("keyword")),
                        intValue(arguments.get("limit")))));
    }

    @Bean
    RegisteredTool createEventTool(EventService eventService, ObjectMapper objectMapper) {
        ToolDefinition definition = new ToolDefinition(
                "create_event",
                "创建单个明确的日历事件",
                schema("calendarSpaceId", "title", "startTime", "endTime", "participantUserIds", "enterpriseFields"),
                RiskLevel.LOW,
                "event:create");
        return new RegisteredTool(definition, (context, arguments) -> ToolExecutionResult.succeeded(
                definition.name(),
                definition.baseRiskLevel(),
                eventService.createEvent(context.userId(), convertWithSpace(
                        objectMapper,
                        arguments,
                        context.calendarSpaceId(),
                        EventCreateRequest.class))));
    }

    @Bean
    RegisteredTool updateEventTool(EventService eventService, ObjectMapper objectMapper) {
        ToolDefinition definition = new ToolDefinition(
                "update_event",
                "修改单个日历事件",
                schema("eventId", "title", "startTime", "endTime", "participantUserIds", "enterpriseFields"),
                RiskLevel.MEDIUM,
                "event:update");
        return new RegisteredTool(definition, (context, arguments) -> ToolExecutionResult.succeeded(
                definition.name(),
                definition.baseRiskLevel(),
                eventService.updateEvent(
                        context.userId(),
                        requireLong(arguments, "eventId"),
                        objectMapper.convertValue(normalizeDateTimeArguments(arguments), EventUpdateRequest.class))));
    }

    @Bean
    RegisteredTool deleteEventTool(EventService eventService) {
        ToolDefinition definition = new ToolDefinition(
                "delete_event",
                "删除单个日历事件",
                schema("eventId"),
                RiskLevel.HIGH,
                "event:delete");
        return new RegisteredTool(definition, (context, arguments) -> ToolExecutionResult.succeeded(
                definition.name(),
                definition.baseRiskLevel(),
                deleteResult(eventService, context.userId(), requireLong(arguments, "eventId"))));
    }

    @Bean
    RegisteredTool checkConflictTool(EventService eventService, ObjectMapper objectMapper) {
        ToolDefinition definition = new ToolDefinition(
                "check_conflict",
                "检查事件时间和参与人冲突",
                schema("calendarSpaceId", "eventId", "participantUserIds", "startTime", "endTime"),
                RiskLevel.LOW,
                "event:read");
        return new RegisteredTool(definition, (context, arguments) -> ToolExecutionResult.succeeded(
                definition.name(),
                definition.baseRiskLevel(),
                eventService.checkConflicts(context.userId(), convertWithSpace(
                        objectMapper,
                        arguments,
                        context.calendarSpaceId(),
                        ConflictCheckRequest.class))));
    }

    @Bean
    RegisteredTool createReminderTool(ReminderService reminderService, ObjectMapper objectMapper) {
        ToolDefinition definition = new ToolDefinition(
                "create_reminder",
                "为事件创建提醒",
                schema("eventId", "offsetMinutes", "triggerAt", "userId"),
                RiskLevel.LOW,
                "reminder:create");
        return new RegisteredTool(definition, (context, arguments) -> ToolExecutionResult.succeeded(
                definition.name(),
                definition.baseRiskLevel(),
                reminderService.createReminder(
                        context.userId(),
                        requireLong(arguments, "eventId"),
                        objectMapper.convertValue(normalizeDateTimeArguments(arguments), CreateReminderRequest.class))));
    }

    @Bean
    RegisteredTool cancelReminderTool(ReminderService reminderService) {
        ToolDefinition definition = new ToolDefinition(
                "cancel_reminder",
                "取消事件提醒",
                schema("reminderId"),
                RiskLevel.HIGH,
                "reminder:cancel");
        return new RegisteredTool(definition, (context, arguments) -> ToolExecutionResult.succeeded(
                definition.name(),
                definition.baseRiskLevel(),
                reminderService.cancelReminder(context.userId(), requireLong(arguments, "reminderId"))));
    }

    @Bean
    RegisteredTool snoozeReminderTool(ReminderService reminderService, ObjectMapper objectMapper) {
        ToolDefinition definition = new ToolDefinition(
                "snooze_reminder",
                "将提醒延后 5、10 或 30 分钟",
                schema("reminderId", "minutes"),
                RiskLevel.MEDIUM,
                "reminder:snooze");
        return new RegisteredTool(definition, (context, arguments) -> ToolExecutionResult.succeeded(
                definition.name(),
                definition.baseRiskLevel(),
                reminderService.snoozeReminder(
                        context.userId(),
                        requireLong(arguments, "reminderId"),
                        objectMapper.convertValue(normalizeDateTimeArguments(arguments), SnoozeReminderRequest.class))));
    }

    private static Map<String, Object> schema(String... fields) {
        return Map.of(
                "type", "object",
                "properties", List.of(fields));
    }

    private static <T> T convertWithSpace(
            ObjectMapper objectMapper,
            Map<String, Object> arguments,
            long calendarSpaceId,
            Class<T> targetType) {
        Map<String, Object> merged = new LinkedHashMap<>(arguments);
        merged.putIfAbsent("calendarSpaceId", calendarSpaceId);
        return objectMapper.convertValue(normalizeDateTimeArguments(merged), targetType);
    }

    private static long requireLong(Map<String, Object> arguments, String field) {
        Long value = longValue(arguments.get(field));
        if (value == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "缺少参数：" + field);
        }
        return value;
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String stringValue = value.toString();
        if (stringValue.isBlank()) {
            return null;
        }
        return Long.parseLong(stringValue);
    }

    private static Integer intValue(Object value) {
        Long longValue = longValue(value);
        return longValue == null ? null : longValue.intValue();
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = value.toString();
        return stringValue.isBlank() ? null : stringValue;
    }

    private static OffsetDateTime offsetDateTime(Object value) {
        String stringValue = stringValue(value);
        if (stringValue == null) {
            return null;
        }
        return parseOffsetDateTime(stringValue);
    }

    private static Map<String, Object> normalizeDateTimeArguments(Map<String, Object> arguments) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        arguments.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedMap = new LinkedHashMap<>();
                nested.forEach((nestedKey, nestedValue) -> {
                    if (nestedKey != null) {
                        nestedMap.put(nestedKey.toString(), nestedValue);
                    }
                });
                normalized.put(key, normalizeDateTimeArguments(nestedMap));
            } else if (isDateTimeField(key) && value instanceof String stringValue) {
                normalized.put(key, normalizeDateTimeString(stringValue));
            } else {
                normalized.put(key, value);
            }
        });
        return normalized;
    }

    private static boolean isDateTimeField(String key) {
        return "start".equals(key)
                || "end".equals(key)
                || "startTime".equals(key)
                || "endTime".equals(key)
                || "repeatUntil".equals(key)
                || "triggerAt".equals(key);
    }

    private static String normalizeDateTimeString(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        return parseOffsetDateTime(normalized).toString();
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value).atZone(DEFAULT_ZONE).toOffsetDateTime();
            } catch (DateTimeParseException exception) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "时间格式不合法：" + value);
            }
        }
    }

    private static Map<String, Object> deleteResult(EventService eventService, long userId, long eventId) {
        boolean deleted = eventService.deleteEvent(userId, eventId);
        return Map.of(
                "eventId", eventId,
                "deleted", deleted);
    }
}
