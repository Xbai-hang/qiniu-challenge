package com.qiniu.challenge.event;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createPersonalEventWritesOrganizerAndCanReadDetail() throws Exception {
        RegisteredUser user = register("event_personal_owner", "event-personal-owner@example.com", "Event Owner");

        MvcResult result = createEvent(user, """
                {
                  "calendarSpaceId": %d,
                  "title": "个人复盘",
                  "startTime": "2026-05-30T10:00:00+08:00",
                  "endTime": "2026-05-30T11:00:00+08:00",
                  "location": "书房",
                  "description": "整理本周事项",
                  "participantUserIds": [%d]
                }
                """.formatted(user.personalSpaceId(), user.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.title").value("个人复盘"))
                .andExpect(jsonPath("$.data.participants[0].userId").value(user.id()))
                .andExpect(jsonPath("$.data.participants[0].role").value("organizer"))
                .andReturn();

        long eventId = data(result).path("id").asLong();
        mockMvc.perform(get("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("个人复盘"))
                .andExpect(jsonPath("$.data.location").value("书房"));
    }

    @Test
    void createEventRejectsUnauthorizedSpace() throws Exception {
        RegisteredUser owner = register("event_space_owner", "event-space-owner@example.com", "Space Owner");
        RegisteredUser other = register("event_space_other", "event-space-other@example.com", "Space Other");

        createEvent(other, """
                {
                  "calendarSpaceId": %d,
                  "title": "越权事件",
                  "startTime": "2026-05-30T10:00:00+08:00",
                  "endTime": "2026-05-30T11:00:00+08:00"
                }
                """.formatted(owner.personalSpaceId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void listEventsFiltersByTimeRangeAndEnterpriseFieldsThenSorts() throws Exception {
        RegisteredUser owner = register("event_filter_owner", "event-filter-owner@example.com", "Filter Owner");
        createEvent(owner, eventJson(owner.personalSpaceId(), "低优先级", "2026-05-30T09:00:00+08:00",
                "2026-05-30T10:00:00+08:00", "Apollo", owner.id(), "todo", "low", "roadmap"));
        createEvent(owner, eventJson(owner.personalSpaceId(), "高优先级", "2026-05-30T11:00:00+08:00",
                "2026-05-30T12:00:00+08:00", "Apollo", owner.id(), "todo", "high", "roadmap"));
        createEvent(owner, eventJson(owner.personalSpaceId(), "其他项目", "2026-05-30T13:00:00+08:00",
                "2026-05-30T14:00:00+08:00", "Hermes", owner.id(), "done", "medium", "other"));

        mockMvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + owner.token())
                        .param("spaceId", String.valueOf(owner.personalSpaceId()))
                        .param("start", "2026-05-30T08:00:00+08:00")
                        .param("end", "2026-05-30T13:00:00+08:00")
                        .param("project", "Apollo")
                        .param("status", "todo")
                        .param("tag", "roadmap")
                        .param("sortBy", "priority")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].project").value("Apollo"))
                .andExpect(jsonPath("$.data[0].priority").value("high"))
                .andExpect(jsonPath("$.data[1].priority").value("low"));
    }

    @Test
    void updateEventReplacesParticipantsAndEnterpriseFields() throws Exception {
        OrganizationFixture fixture = createOrganizationFixture("event_update");
        MvcResult created = createEvent(fixture.owner(), """
                {
                  "calendarSpaceId": %d,
                  "title": "项目同步",
                  "startTime": "2026-05-30T10:00:00+08:00",
                  "endTime": "2026-05-30T11:00:00+08:00",
                  "participantUserIds": [%d],
                  "enterpriseFields": {
                    "project": "Apollo",
                    "ownerUserId": %d,
                    "status": "todo",
                    "priority": "medium",
                    "tags": ["sync"]
                  }
                }
                """.formatted(
                        fixture.spaceId(),
                        fixture.member().id(),
                        fixture.owner().id()))
                .andExpect(status().isOk())
                .andReturn();
        long eventId = data(created).path("id").asLong();

        mockMvc.perform(patch("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + fixture.owner().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "项目同步更新",
                                  "participantUserIds": [%d],
                                  "enterpriseFields": {
                                    "project": "Apollo",
                                    "ownerUserId": %d,
                                    "status": "in_progress",
                                    "priority": "high",
                                    "tags": ["sync", "release"]
                                  }
                                }
                                """.formatted(fixture.admin().id(), fixture.admin().id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("项目同步更新"))
                .andExpect(jsonPath("$.data.ownerUserId").value(fixture.admin().id()))
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andExpect(jsonPath("$.data.priority").value("high"))
                .andExpect(jsonPath("$.data.tags", hasItem("release")))
                .andExpect(jsonPath("$.data.participants.length()").value(2))
                .andExpect(jsonPath("$.data.participants[0].userId").value(fixture.owner().id()))
                .andExpect(jsonPath("$.data.participants[1].userId").value(fixture.admin().id()));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + fixture.admin().token())
                        .param("status", "unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("event_invite"))
                .andExpect(jsonPath("$.data.items[0].title")
                        .value(org.hamcrest.Matchers.containsString("项目同步更新")));
    }

    @Test
    void creatingOrganizationEventNotifiesAddedParticipants() throws Exception {
        OrganizationFixture fixture = createOrganizationFixture("event_invite_create");

        createEvent(fixture.owner(), """
                {
                  "calendarSpaceId": %d,
                  "title": "企业评审会",
                  "startTime": "2026-05-30T10:00:00+08:00",
                  "endTime": "2026-05-30T11:00:00+08:00",
                  "location": "会议室 B",
                  "participantUserIds": [%d]
                }
                """.formatted(fixture.spaceId(), fixture.member().id()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + fixture.member().token())
                        .param("status", "unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("event_invite"))
                .andExpect(jsonPath("$.data.items[0].reminderId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].title").value("你被添加到日程「企业评审会」"))
                .andExpect(jsonPath("$.data.items[0].content")
                        .value(org.hamcrest.Matchers.containsString("会议室 B")));
    }

    @Test
    void memberCannotUpdateOrganizationEventCreatedByAnotherUser() throws Exception {
        OrganizationFixture fixture = createOrganizationFixture("event_member_write");
        MvcResult created = createEvent(fixture.owner(), """
                {
                  "calendarSpaceId": %d,
                  "title": "Owner 事件",
                  "startTime": "2026-05-30T10:00:00+08:00",
                  "endTime": "2026-05-30T11:00:00+08:00",
                  "participantUserIds": [%d]
                }
                """.formatted(fixture.spaceId(), fixture.member().id()))
                .andExpect(status().isOk())
                .andReturn();
        long eventId = data(created).path("id").asLong();

        mockMvc.perform(patch("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + fixture.member().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Member 越权修改"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanUpdateOrganizationEventEvenWhenVisibilityIsParticipants() throws Exception {
        OrganizationFixture fixture = createOrganizationFixture("event_admin_write");
        MvcResult created = createEvent(fixture.owner(), """
                {
                  "calendarSpaceId": %d,
                  "title": "仅参与人可见",
                  "startTime": "2026-05-30T10:00:00+08:00",
                  "endTime": "2026-05-30T11:00:00+08:00",
                  "visibility": "participants",
                  "participantUserIds": [%d]
                }
                """.formatted(fixture.spaceId(), fixture.owner().id()))
                .andExpect(status().isOk())
                .andReturn();
        long eventId = data(created).path("id").asLong();

        mockMvc.perform(patch("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + fixture.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Admin 已修改"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Admin 已修改"));
    }

    @Test
    void manualEventWritesCreateUpdateAndDeleteOperationLogs() throws Exception {
        RegisteredUser user = register("event_log_owner", "event-log-owner@example.com", "Log Owner");
        MvcResult created = createEvent(user, eventJson(user.personalSpaceId(), "日志事件", "2026-05-30T09:00:00+08:00",
                "2026-05-30T10:00:00+08:00", "Apollo", user.id(), "todo", "low", "log"))
                .andExpect(status().isOk())
                .andReturn();
        long eventId = data(created).path("id").asLong();

        mockMvc.perform(patch("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "日志事件更新"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk());

        Integer logCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM operation_logs
                WHERE user_id = ?
                  AND target_type = 'event'
                  AND target_id = ?
                  AND operation_source = 'manual'
                  AND operation_type IN ('create', 'update', 'delete')
                """, Integer.class, user.id(), eventId);
        org.hamcrest.MatcherAssert.assertThat(logCount, org.hamcrest.Matchers.is(3));
    }

    @Test
    void operationLogsCanBeListedAndExported() throws Exception {
        RegisteredUser user = register("event_log_query", "event-log-query@example.com", "Log Query");
        MvcResult created = createEvent(user, eventJson(user.personalSpaceId(), "可审计事件", "2026-05-30T09:00:00+08:00",
                "2026-05-30T10:00:00+08:00", "Apollo", user.id(), "todo", "low", "audit"))
                .andExpect(status().isOk())
                .andReturn();
        long eventId = data(created).path("id").asLong();

        mockMvc.perform(get("/api/operation-logs")
                        .header("Authorization", "Bearer " + user.token())
                        .param("calendarSpaceId", String.valueOf(user.personalSpaceId()))
                        .param("operationSource", "manual")
                        .param("targetType", "event")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].operationSource").value("manual"))
                .andExpect(jsonPath("$.data.items[0].operationType").value("create"))
                .andExpect(jsonPath("$.data.items[0].targetType").value("event"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(eventId))
                .andExpect(jsonPath("$.data.items[0].afterSnapshot")
                        .value(org.hamcrest.Matchers.containsString("可审计事件")));

        mockMvc.perform(get("/api/operation-logs/export")
                        .header("Authorization", "Bearer " + user.token())
                        .param("calendarSpaceId", String.valueOf(user.personalSpaceId()))
                        .param("operationSource", "manual")
                        .param("targetType", "event"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("operation-logs.csv")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("可审计事件")));
    }

    @Test
    void conflictCreateAndUpdateRequireConfirmationUnlessForced() throws Exception {
        RegisteredUser user = register("event_conflict_owner", "event-conflict-owner@example.com", "Conflict Owner");
        MvcResult first = createEvent(user, """
                {
                  "calendarSpaceId": %d,
                  "title": "已有会议",
                  "startTime": "2026-05-30T10:00:00+08:00",
                  "endTime": "2026-05-30T11:00:00+08:00",
                  "participantUserIds": [%d]
                }
                """.formatted(user.personalSpaceId(), user.id()))
                .andExpect(status().isOk())
                .andReturn();
        long firstEventId = data(first).path("id").asLong();

        createEvent(user, """
                {
                  "calendarSpaceId": %d,
                  "title": "冲突会议",
                  "startTime": "2026-05-30T10:30:00+08:00",
                  "endTime": "2026-05-30T11:30:00+08:00",
                  "participantUserIds": [%d]
                }
                """.formatted(user.personalSpaceId(), user.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.details.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.error.details.conflicts[0].eventId").value(0))
                .andExpect(jsonPath("$.error.details.conflicts[0].title").value("已有安排"))
                .andExpect(jsonPath("$.error.details.conflicts[0].participantUserId").value(user.id()));

        MvcResult second = createEvent(user, """
                {
                  "calendarSpaceId": %d,
                  "title": "冲突会议",
                  "startTime": "2026-05-30T10:30:00+08:00",
                  "endTime": "2026-05-30T11:30:00+08:00",
                  "participantUserIds": [%d],
                  "forceCreateOnConflict": true
                }
                """.formatted(user.personalSpaceId(), user.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.data.conflicts[0].eventId").value(0))
                .andExpect(jsonPath("$.data.conflicts[0].participantUserId").value(user.id()))
                .andReturn();
        long secondEventId = data(second).path("id").asLong();

        mockMvc.perform(patch("/api/events/{eventId}", secondEventId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "2026-05-30T11:30:00+08:00",
                                  "endTime": "2026-05-30T12:30:00+08:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
                .andExpect(jsonPath("$.data.conflicts.length()").value(0));

        mockMvc.perform(patch("/api/events/{eventId}", secondEventId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "2026-05-30T10:15:00+08:00",
                                  "endTime": "2026-05-30T10:45:00+08:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.details.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.error.details.conflicts[0].eventId").value(0))
                .andExpect(jsonPath("$.error.details.conflicts[0].participantUserId").value(user.id()));

        mockMvc.perform(patch("/api/events/{eventId}", secondEventId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "2026-05-30T10:15:00+08:00",
                                  "endTime": "2026-05-30T10:45:00+08:00",
                                  "forceUpdateOnConflict": true
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.data.conflicts[0].eventId").value(0))
                .andExpect(jsonPath("$.data.conflicts[0].participantUserId").value(user.id()));
    }

    @Test
    void conflictCheckEndpointReturnsParticipantConflicts() throws Exception {
        RegisteredUser user = register("event_conflict_check", "event-conflict-check@example.com", "Conflict Check");
        MvcResult created = createEvent(user, """
                {
                  "calendarSpaceId": %d,
                  "title": "待检测会议",
                  "startTime": "2026-05-30T14:00:00+08:00",
                  "endTime": "2026-05-30T15:00:00+08:00",
                  "participantUserIds": [%d]
                }
                """.formatted(user.personalSpaceId(), user.id()))
                .andExpect(status().isOk())
                .andReturn();
        long eventId = data(created).path("id").asLong();

        mockMvc.perform(post("/api/events/conflicts/check")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "participantUserIds": [%d],
                                  "startTime": "2026-05-30T14:30:00+08:00",
                                  "endTime": "2026-05-30T15:30:00+08:00"
                                }
                                """.formatted(user.personalSpaceId(), user.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasConflict").value(true))
                .andExpect(jsonPath("$.data.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.data.conflicts[0].eventId").value(0))
                .andExpect(jsonPath("$.data.conflicts[0].title").value("已有安排"))
                .andExpect(jsonPath("$.data.conflicts[0].participantUserId").value(user.id()));
    }

    @Test
    void organizationEventDetectsMemberPersonalEventConflict() throws Exception {
        OrganizationFixture fixture = createOrganizationFixture("event_cross_space_conflict");
        MvcResult personal = createEvent(fixture.member(), """
                {
                  "calendarSpaceId": %d,
                  "title": "成员个人安排",
                  "startTime": "2026-05-30T16:00:00+08:00",
                  "endTime": "2026-05-30T17:00:00+08:00",
                  "participantUserIds": [%d]
                }
                """.formatted(fixture.member().personalSpaceId(), fixture.member().id()))
                .andExpect(status().isOk())
                .andReturn();
        long personalEventId = data(personal).path("id").asLong();

        mockMvc.perform(post("/api/events/conflicts/check")
                        .header("Authorization", "Bearer " + fixture.owner().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "participantUserIds": [%d],
                                  "startTime": "2026-05-30T16:30:00+08:00",
                                  "endTime": "2026-05-30T17:30:00+08:00"
                                }
                """.formatted(fixture.spaceId(), fixture.member().id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasConflict").value(true))
                .andExpect(jsonPath("$.data.conflicts[0].eventId").value(0))
                .andExpect(jsonPath("$.data.conflicts[0].participantUserId").value(fixture.member().id()));

        createEvent(fixture.owner(), """
                {
                  "calendarSpaceId": %d,
                  "title": "企业冲突会议",
                  "startTime": "2026-05-30T16:30:00+08:00",
                  "endTime": "2026-05-30T17:30:00+08:00",
                  "participantUserIds": [%d]
                }
                """.formatted(fixture.spaceId(), fixture.member().id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.error.details.conflicts[0].eventId").value(0))
                .andExpect(jsonPath("$.error.details.conflicts[0].participantUserId").value(fixture.member().id()));

        createEvent(fixture.owner(), """
                {
                  "calendarSpaceId": %d,
                  "title": "企业冲突会议",
                  "startTime": "2026-05-30T16:30:00+08:00",
                  "endTime": "2026-05-30T17:30:00+08:00",
                  "participantUserIds": [%d],
                  "forceCreateOnConflict": true
                }
                """.formatted(fixture.spaceId(), fixture.member().id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.data.conflicts[0].eventId").value(0))
                .andExpect(jsonPath("$.data.conflicts[0].participantUserId").value(fixture.member().id()));
    }

    @Test
    void enterpriseEventRejectsParticipantOutsideOrganization() throws Exception {
        OrganizationFixture fixture = createOrganizationFixture("event_outsider");
        RegisteredUser outsider = register("event_outsider_user", "event-outsider-user@example.com", "Outsider");

        createEvent(fixture.owner(), """
                {
                  "calendarSpaceId": %d,
                  "title": "外部参与人",
                  "startTime": "2026-05-30T10:00:00+08:00",
                  "endTime": "2026-05-30T11:00:00+08:00",
                  "participantUserIds": [%d]
                }
                """.formatted(fixture.spaceId(), outsider.id()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void searchEventsDoesNotLeakAcrossSpaces() throws Exception {
        RegisteredUser owner = register("event_search_owner", "event-search-owner@example.com", "Search Owner");
        RegisteredUser other = register("event_search_other", "event-search-other@example.com", "Search Other");
        createEvent(owner, eventJson(owner.personalSpaceId(), "Alpha 方案评审", "2026-05-30T09:00:00+08:00",
                "2026-05-30T10:00:00+08:00", "Apollo", owner.id(), "todo", "high", "alpha"));
        createEvent(other, eventJson(other.personalSpaceId(), "Alpha 私密会议", "2026-05-30T09:00:00+08:00",
                "2026-05-30T10:00:00+08:00", "Apollo", other.id(), "todo", "high", "alpha"));

        mockMvc.perform(get("/api/events/search")
                        .header("Authorization", "Bearer " + owner.token())
                        .param("keyword", "Alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Alpha 方案评审"));
    }

    @Test
    void deleteEventSoftDeletesAndHidesDetail() throws Exception {
        RegisteredUser user = register("event_delete_owner", "event-delete-owner@example.com", "Delete Owner");
        MvcResult created = createEvent(user, eventJson(user.personalSpaceId(), "待删除", "2026-05-30T09:00:00+08:00",
                "2026-05-30T10:00:00+08:00", "Apollo", user.id(), "todo", "low", "cleanup"))
                .andExpect(status().isOk())
                .andReturn();
        long eventId = data(created).path("id").asLong();

        mockMvc.perform(delete("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        Integer retained = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM calendar_events WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                eventId);
        org.hamcrest.MatcherAssert.assertThat(retained, org.hamcrest.Matchers.is(1));
    }

    private org.springframework.test.web.servlet.ResultActions createEvent(RegisteredUser user, String json)
            throws Exception {
        return mockMvc.perform(post("/api/events")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String eventJson(
            long spaceId,
            String title,
            String startTime,
            String endTime,
            String project,
            long ownerUserId,
            String status,
            String priority,
            String tag) {
        return """
                {
                  "calendarSpaceId": %d,
                  "title": "%s",
                  "startTime": "%s",
                  "endTime": "%s",
                  "description": "包含关键词 %s",
                  "participantUserIds": [%d],
                  "enterpriseFields": {
                    "project": "%s",
                    "ownerUserId": %d,
                    "status": "%s",
                    "priority": "%s",
                    "tags": ["%s"],
                    "eventType": "meeting"
                  }
                }
                """.formatted(
                spaceId,
                title,
                startTime,
                endTime,
                tag,
                ownerUserId,
                project,
                ownerUserId,
                status,
                priority,
                tag);
    }

    private OrganizationFixture createOrganizationFixture(String prefix) throws Exception {
        RegisteredUser owner = register(prefix + "_owner", prefix + "-owner@example.com", "Org Owner");
        RegisteredUser admin = register(prefix + "_admin", prefix + "-admin@example.com", "Org Admin");
        RegisteredUser member = register(prefix + "_member", prefix + "-member@example.com", "Org Member");
        JsonNode organization = data(mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s 团队"
                                }
                                """.formatted(prefix)))
                .andExpect(status().isOk())
                .andReturn());
        joinOrganization(admin, organization.path("inviteCode").asText());
        joinOrganization(member, organization.path("inviteCode").asText());
        mockMvc.perform(patch("/api/organizations/{organizationId}/members/{userId}/role",
                        organization.path("organizationId").asLong(),
                        admin.id())
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "admin"
                                }
                                """))
                .andExpect(status().isOk());
        return new OrganizationFixture(owner, admin, member, organization.path("spaceId").asLong());
    }

    private void joinOrganization(RegisteredUser user, String inviteCode) throws Exception {
        mockMvc.perform(post("/api/organizations/join")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inviteCode": "%s"
                                }
                                """.formatted(inviteCode)))
                .andExpect(status().isOk());
    }

    private RegisteredUser register(String username, String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "displayName": "%s",
                                  "password": "Password123"
                                }
                                """.formatted(username, email, displayName)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = data(result);
        long userId = data.path("user").path("id").asLong();
        Long personalSpaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM calendar_spaces WHERE type = 'personal' AND owner_user_id = ? AND deleted_at IS NULL",
                Long.class,
                userId);
        return new RegisteredUser(userId, data.path("accessToken").asText(), personalSpaceId);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private record RegisteredUser(long id, String token, long personalSpaceId) {
    }

    private record OrganizationFixture(
            RegisteredUser owner,
            RegisteredUser admin,
            RegisteredUser member,
            long spaceId) {
    }
}
