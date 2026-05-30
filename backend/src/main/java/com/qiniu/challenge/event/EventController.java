package com.qiniu.challenge.event;

import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ApiResponse<EventResponse> createEvent(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody EventCreateRequest request) {
        return ApiResponse.success(eventService.createEvent(principal.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<EventResponse>> listEvents(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) Long calendarSpaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection) {
        Long resolvedSpaceId = calendarSpaceId == null ? spaceId : calendarSpaceId;
        return ApiResponse.success(eventService.listEvents(
                principal.userId(),
                resolvedSpaceId,
                start,
                end,
                keyword,
                project,
                ownerUserId,
                status,
                priority,
                tag,
                sortBy,
                sortDirection));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventResponse> getEvent(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long eventId) {
        return ApiResponse.success(eventService.getEvent(principal.userId(), eventId));
    }

    @PatchMapping("/{eventId}")
    public ApiResponse<EventResponse> updateEvent(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long eventId,
            @Valid @RequestBody EventUpdateRequest request) {
        return ApiResponse.success(eventService.updateEvent(principal.userId(), eventId, request));
    }

    @DeleteMapping("/{eventId}")
    public ApiResponse<Boolean> deleteEvent(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long eventId) {
        return ApiResponse.success(eventService.deleteEvent(principal.userId(), eventId));
    }

    @GetMapping("/search")
    public ApiResponse<List<EventResponse>> searchEvents(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) Long calendarSpaceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        Long resolvedSpaceId = calendarSpaceId == null ? spaceId : calendarSpaceId;
        return ApiResponse.success(eventService.searchEvents(principal.userId(), resolvedSpaceId, keyword, limit));
    }
}
