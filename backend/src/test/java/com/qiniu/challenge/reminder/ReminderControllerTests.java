package com.qiniu.challenge.reminder;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
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
class ReminderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReminderService reminderService;

    @Test
    void reminderCrudSupportsCreateListUpdateAndCancel() throws Exception {
        RegisteredUser user = register("reminder_crud_owner", "reminder-crud-owner@example.com", "Reminder Owner");
        long eventId = createEvent(user, "提醒 CRUD", "2026-05-30T10:00:00+08:00", "2026-05-30T11:00:00+08:00");

        MvcResult created = mockMvc.perform(post("/api/events/{eventId}/reminders", eventId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "offsetMinutes": 15
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.eventId").value(eventId))
                .andExpect(jsonPath("$.data.offsetMinutes").value(15))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andReturn();
        long reminderId = data(created).path("id").asLong();

        mockMvc.perform(get("/api/events/{eventId}/reminders", eventId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(reminderId));

        mockMvc.perform(patch("/api/reminders/{reminderId}", reminderId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "offsetMinutes": 30
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offsetMinutes").value(30))
                .andExpect(jsonPath("$.data.triggerAt").value("2026-05-30T09:30:00+08:00"));

        mockMvc.perform(post("/api/reminders/{reminderId}/cancel", reminderId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM event_reminders WHERE id = ?",
                String.class,
                reminderId);
        MatcherAssert.assertThat(status, Matchers.is("cancelled"));
    }

    @Test
    void dueReminderScanCreatesNotificationAndNotificationCanBeReadAndSnoozed() throws Exception {
        RegisteredUser user = register("reminder_notify_owner", "reminder-notify-owner@example.com", "Notify Owner");
        long eventId = createEvent(user, "到点提醒", "2026-05-30T10:00:00+08:00", "2026-05-30T11:00:00+08:00");

        MvcResult created = mockMvc.perform(post("/api/events/{eventId}/reminders", eventId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "triggerAt": "2026-05-29T10:00:00+08:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long reminderId = data(created).path("id").asLong();

        reminderService.scanDueReminders();

        MvcResult page = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + user.token())
                        .param("status", "unread")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.items[0].reminderId").value(reminderId))
                .andExpect(jsonPath("$.data.items[0].title").value("到点提醒 即将开始"))
                .andReturn();
        long notificationId = data(page).path("items").get(0).path("id").asLong();

        mockMvc.perform(post("/api/notifications/{notificationId}/read", notificationId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("read"));

        mockMvc.perform(post("/api/reminders/{reminderId}/snooze", reminderId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minutes": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oldReminderId").value(reminderId))
                .andExpect(jsonPath("$.data.newReminderId", notNullValue()))
                .andExpect(jsonPath("$.data.status").value("pending"));

        Integer snoozedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM event_reminders
                WHERE snoozed_from_id = ?
                  AND status = 'pending'
                """, Integer.class, reminderId);
        MatcherAssert.assertThat(snoozedCount, Matchers.is(1));
    }

    @Test
    void otherUserCannotCreateReminderInPersonalSpace() throws Exception {
        RegisteredUser owner = register("reminder_space_owner", "reminder-space-owner@example.com", "Space Owner");
        RegisteredUser other = register("reminder_space_other", "reminder-space-other@example.com", "Space Other");
        long eventId = createEvent(owner, "私有事件", "2026-05-30T10:00:00+08:00", "2026-05-30T11:00:00+08:00");

        mockMvc.perform(post("/api/events/{eventId}/reminders", eventId)
                        .header("Authorization", "Bearer " + other.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "offsetMinutes": 5
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private long createEvent(RegisteredUser user, String title, String startTime, String endTime) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "title": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "participantUserIds": [%d]
                                }
                                """.formatted(user.personalSpaceId(), title, startTime, endTime, user.id())))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("id").asLong();
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
}
