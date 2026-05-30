package com.qiniu.challenge.event;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import com.qiniu.challenge.organization.OrganizationRole;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final EventRepository eventRepository;

    public PermissionService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public CalendarSpaceAccess requireSpaceAccess(long spaceId, long currentUserId) {
        return eventRepository.findAccessibleSpace(spaceId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "无权访问该日历空间"));
    }

    public void requireCanCreateEvent(CalendarSpaceAccess space) {
        if (space.personal() || space.organization()) {
            return;
        }
        throw new ApiException(ErrorCode.FORBIDDEN);
    }

    public void requireCanUpdateEvent(CalendarSpaceAccess space, CalendarEvent event, long currentUserId) {
        if (!canWriteEvent(space, event, currentUserId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "无权修改该事件");
        }
    }

    public void requireCanDeleteEvent(CalendarSpaceAccess space, CalendarEvent event, long currentUserId) {
        if (!canWriteEvent(space, event, currentUserId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "无权删除该事件");
        }
    }

    public void requireCanViewOperationLogs(CalendarSpaceAccess space, long currentUserId) {
        if (space.personal()) {
            if (space.ownerUserId() != null && space.ownerUserId() == currentUserId) {
                return;
            }
            throw new ApiException(ErrorCode.FORBIDDEN, "无权查看该空间操作日志");
        }
        if (!space.organization()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "无权查看该空间操作日志");
        }
        OrganizationRole role = OrganizationRole.fromValue(space.role());
        if (role == OrganizationRole.OWNER || role == OrganizationRole.ADMIN) {
            return;
        }
        throw new ApiException(ErrorCode.FORBIDDEN, "仅组织 Owner/Admin 可查看操作日志");
    }

    private boolean canWriteEvent(CalendarSpaceAccess space, CalendarEvent event, long currentUserId) {
        if (space.personal()) {
            return space.ownerUserId() != null && space.ownerUserId() == currentUserId;
        }
        if (!space.organization()) {
            return false;
        }
        OrganizationRole role = OrganizationRole.fromValue(space.role());
        if (role == OrganizationRole.OWNER || role == OrganizationRole.ADMIN) {
            return true;
        }
        return event.createdBy() == currentUserId;
    }
}
