package com.qiniu.challenge.event;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConflictService {

    private final EventRepository eventRepository;
    private final PermissionService permissionService;

    public ConflictService(EventRepository eventRepository, PermissionService permissionService) {
        this.eventRepository = eventRepository;
        this.permissionService = permissionService;
    }

    public ConflictCheckResponse checkConflicts(long currentUserId, ConflictCheckRequest request) {
        permissionService.requireSpaceAccess(request.calendarSpaceId(), currentUserId);
        validateTimeRange(request.startTime(), request.endTime());
        List<Long> participantUserIds = normalizeParticipants(currentUserId, request.participantUserIds());
        return ConflictCheckResponse.of(eventRepository.findConflicts(
                currentUserId,
                participantUserIds,
                request.startTime(),
                request.endTime(),
                request.eventId()));
    }

    public List<EventConflict> detectConflicts(
            long currentUserId,
            List<Long> participantUserIds,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            Long excludeEventId) {
        List<Long> normalizedParticipants = normalizeParticipants(currentUserId, participantUserIds);
        return eventRepository.findConflicts(
                currentUserId,
                normalizedParticipants,
                startTime,
                endTime,
                excludeEventId);
    }

    private List<Long> normalizeParticipants(long currentUserId, List<Long> participantUserIds) {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        if (participantUserIds != null) {
            userIds.addAll(participantUserIds);
        }
        if (userIds.isEmpty()) {
            userIds.add(currentUserId);
        }
        return userIds.stream().toList();
    }

    private void validateTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
    }
}
