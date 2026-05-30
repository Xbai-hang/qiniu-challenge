package com.qiniu.challenge.ai;

import com.qiniu.challenge.event.EventRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RiskEvaluator {

    private final EventRepository eventRepository;

    public RiskEvaluator(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public RiskLevel evaluate(RiskEvaluationRequest request) {
        RiskLevel risk = request.tool().baseRiskLevel();
        Map<String, Object> arguments = request.arguments();
        String toolName = request.tool().name();

        if (toolName.contains("delete") || toolName.contains("cancel")) {
            risk = risk.max(RiskLevel.HIGH);
        }
        if (isBatch(arguments)) {
            risk = risk.max(RiskLevel.HIGH);
        }
        if (Boolean.TRUE.equals(arguments.get("crossSpace"))) {
            risk = risk.max(RiskLevel.CRITICAL);
        }
        Long eventId = longValue(arguments.get("eventId"));
        if (eventId != null && affectsAnotherUsersEvent(request.context().userId(), eventId)) {
            risk = risk.max(RiskLevel.HIGH);
        }
        if (Boolean.TRUE.equals(arguments.get("hasConflict"))
                || Boolean.TRUE.equals(arguments.get("forceCreateOnConflict"))
                || Boolean.TRUE.equals(arguments.get("forceUpdateOnConflict"))) {
            risk = risk.max(RiskLevel.HIGH);
        }
        return risk;
    }

    private boolean affectsAnotherUsersEvent(long currentUserId, long eventId) {
        return eventRepository.findEvent(eventId)
                .map(event -> event.createdBy() != currentUserId)
                .orElse(false);
    }

    private boolean isBatch(Map<String, Object> arguments) {
        Object eventIds = arguments.get("eventIds");
        if (eventIds instanceof List<?> list && list.size() > 1) {
            return true;
        }
        Object batchSize = arguments.get("batchSize");
        Long count = longValue(batchSize);
        return count != null && count > 1;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return null;
    }
}
