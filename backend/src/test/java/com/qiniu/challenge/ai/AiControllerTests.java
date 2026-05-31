package com.qiniu.challenge.ai;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.util.List;
import java.util.Map;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AiControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiModelClient aiModelClient;

    @BeforeEach
    void setUpAiModelClient() {
        reset(aiModelClient);
        when(aiModelClient.provider()).thenReturn("test-model");
    }

    @Test
    void conversationsAndMessagesArePersisted() throws Exception {
        RegisteredUser user = register("ai_conversation_user", "ai-conversation-user@example.com", "AI User");

        MvcResult conversationResult = mockMvc.perform(post("/api/ai/conversations")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "title": "今日安排",
                                  "channel": "text"
                                }
                                """.formatted(user.personalSpaceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.aiPromptVersion").value("prompt-v1"))
                .andExpect(jsonPath("$.data.toolSchemaVersion").value("tool-schema-v1"))
                .andReturn();
        long conversationId = data(conversationResult).path("id").asLong();

        mockMvc.perform(post("/api/ai/conversations/{conversationId}/messages", conversationId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "user",
                                  "inputMode": "text",
                                  "content": "今天有什么安排"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("user"))
                .andExpect(jsonPath("$.data.content").value("今天有什么安排"));

        mockMvc.perform(get("/api/ai/conversations/{conversationId}/messages", conversationId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("今天有什么安排"));
    }

    @Test
    void conversationCanBeDeletedFromHistory() throws Exception {
        RegisteredUser user = register("ai_conversation_delete_user", "ai-conversation-delete-user@example.com", "AI Delete History");
        long conversationId = createConversation(user, "待删除会话");

        mockMvc.perform(post("/api/ai/conversations/{conversationId}/messages", conversationId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "user",
                                  "inputMode": "text",
                                  "content": "你好"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/ai/conversations/{conversationId}", conversationId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/ai/conversations")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", Matchers.not(Matchers.hasItem((int) conversationId))));

        mockMvc.perform(get("/api/ai/conversations/{conversationId}/messages", conversationId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void registryListsExpectedCalendarAndReminderTools() throws Exception {
        RegisteredUser user = register("ai_tool_list_user", "ai-tool-list-user@example.com", "AI Tool List");

        mockMvc.perform(get("/api/ai/tools")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", hasItem("list_events")))
                .andExpect(jsonPath("$.data[*].name", hasItem("search_events")))
                .andExpect(jsonPath("$.data[*].name", hasItem("create_event")))
                .andExpect(jsonPath("$.data[*].name", hasItem("update_event")))
                .andExpect(jsonPath("$.data[*].name", hasItem("delete_event")))
                .andExpect(jsonPath("$.data[*].name", hasItem("check_conflict")))
                .andExpect(jsonPath("$.data[*].name", hasItem("create_reminder")))
                .andExpect(jsonPath("$.data[*].name", hasItem("cancel_reminder")))
                .andExpect(jsonPath("$.data[*].name", hasItem("snooze_reminder")));
    }

    @Test
    void lowRiskCreateAndListToolsExecuteAndWriteSucceededLogs() throws Exception {
        RegisteredUser user = register("ai_tool_exec_user", "ai-tool-exec-user@example.com", "AI Tool Exec");
        long conversationId = createConversation(user, "工具执行");

        mockMvc.perform(post("/api/ai/tools/create_event/execute")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": %d,
                                  "calendarSpaceId": %d,
                                  "arguments": {
                                    "title": "AI 创建事件",
                                    "startTime": "2026-05-30T10:00:00+08:00",
                                    "endTime": "2026-05-30T11:00:00+08:00",
                                    "participantUserIds": [%d]
                                  }
                                }
                                """.formatted(conversationId, user.personalSpaceId(), user.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolName").value("create_event"))
                .andExpect(jsonPath("$.data.riskLevel").value("low"))
                .andExpect(jsonPath("$.data.status").value("succeeded"))
                .andExpect(jsonPath("$.data.data.title").value("AI 创建事件"));

        mockMvc.perform(post("/api/ai/tools/list_events/execute")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": %d,
                                  "calendarSpaceId": %d,
                                  "arguments": {
                                    "start": "2026-05-30T00:00:00+08:00",
                                    "end": "2026-05-31T00:00:00+08:00"
                                  }
                                }
                                """.formatted(conversationId, user.personalSpaceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("succeeded"))
                .andExpect(jsonPath("$.data.data[0].title").value("AI 创建事件"));

        mockMvc.perform(get("/api/ai/tool-call-logs")
                        .header("Authorization", "Bearer " + user.token())
                        .param("conversationId", String.valueOf(conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("succeeded"));
    }

    @Test
    void highRiskDeleteToolDoesNotExecuteAndWritesConfirmationRequiredLog() throws Exception {
        RegisteredUser user = register("ai_delete_user", "ai-delete-user@example.com", "AI Delete");
        long conversationId = createConversation(user, "删除确认");
        long eventId = createEvent(user, "待确认删除");

        mockMvc.perform(post("/api/ai/tools/delete_event/execute")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": %d,
                                  "calendarSpaceId": %d,
                                  "arguments": {
                                    "eventId": %d
                                  }
                                }
                                """.formatted(conversationId, user.personalSpaceId(), eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("confirmation_required"))
                .andExpect(jsonPath("$.data.confirmationRequired").value(true))
                .andExpect(jsonPath("$.data.riskLevel").value("high"));

        Integer retained = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM calendar_events WHERE id = ? AND deleted_at IS NULL",
                Integer.class,
                eventId);
        MatcherAssert.assertThat(retained, Matchers.is(1));

        mockMvc.perform(get("/api/ai/tool-call-logs")
                        .header("Authorization", "Bearer " + user.token())
                        .param("conversationId", String.valueOf(conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("confirmation_required"))
                .andExpect(jsonPath("$.data.items[0].riskLevel").value("high"));
    }

    @Test
    void failedToolCallWritesFailedLog() throws Exception {
        RegisteredUser user = register("ai_failed_user", "ai-failed-user@example.com", "AI Failed");
        long conversationId = createConversation(user, "失败日志");

        mockMvc.perform(post("/api/ai/tools/create_event/execute")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": %d,
                                  "calendarSpaceId": %d,
                                  "arguments": {
                                    "title": "越权参与人",
                                    "startTime": "2026-05-30T10:00:00+08:00",
                                    "endTime": "2026-05-30T11:00:00+08:00",
                                    "participantUserIds": [999999]
                                  }
                                }
                                """.formatted(conversationId, user.personalSpaceId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/ai/tool-call-logs")
                        .header("Authorization", "Bearer " + user.token())
                        .param("conversationId", String.valueOf(conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("failed"))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("FORBIDDEN"));
    }

    @Test
    void chatCanCreateEventAndUndoLastAiOperation() throws Exception {
        RegisteredUser user = register("ai_chat_create_user", "ai-chat-create-user@example.com", "AI Chat Create");
        when(aiModelClient.chat(any()))
                .thenReturn(new AiModelResponse("test-model", "test", "", List.of(new AiRequestedToolCall(
                        "call_create_event",
                        "create_event",
                        Map.of(
                                "title", "项目复盘",
                                "startTime", "2026-05-30T15:00:00+08:00",
                                "endTime", "2026-05-30T16:00:00+08:00",
                                "participantUserIds", List.of(user.id()))))))
                .thenReturn(new AiModelResponse("test-model", "test", "已为你创建「项目复盘」。", List.of()));

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "inputMode": "text",
                                  "message": "明天下午三点安排项目复盘"
                                }
                                """.formatted(user.personalSpaceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId", notNullValue()))
                .andExpect(jsonPath("$.data.resultCard.type").value("event_created"))
                .andExpect(jsonPath("$.data.toolCalls[0].toolName").value("create_event"))
                .andReturn();
        long eventId = data(chatResult).path("resultCard").path("eventId").asLong();

        Integer created = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM calendar_events WHERE id = ? AND deleted_at IS NULL",
                Integer.class,
                eventId);
        MatcherAssert.assertThat(created, Matchers.is(1));

        mockMvc.perform(post("/api/ai/undo-last")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d
                                }
                                """.formatted(user.personalSpaceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.undone").value(true));

        Integer visibleAfterUndo = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM calendar_events WHERE id = ? AND deleted_at IS NULL",
                Integer.class,
                eventId);
        MatcherAssert.assertThat(visibleAfterUndo, Matchers.is(0));
    }

    @Test
    void chatListEventsIncludesEventDetailsInReply() throws Exception {
        RegisteredUser user = register("ai_chat_list_user", "ai-chat-list-user@example.com", "AI Chat List");
        createEvent(user, "范围内日程");
        when(aiModelClient.chat(any()))
                .thenReturn(new AiModelResponse("test-model", "test", "", List.of(new AiRequestedToolCall(
                        "call_list_events",
                        "list_events",
                        Map.of(
                                "start", "2026-05-30T00:00:00+08:00",
                                "end", "2026-05-31T00:00:00+08:00")))))
                .thenReturn(new AiModelResponse("test-model", "test", "当前范围内有 1 个日程：范围内日程，05-30 12:00 开始。", List.of()));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "inputMode": "text",
                                  "message": "当前范围内的日程请你告诉我"
                                }
                                """.formatted(user.personalSpaceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value(Matchers.containsString("范围内日程")))
                .andExpect(jsonPath("$.data.reply").value(Matchers.containsString("05-30 12:00")))
                .andExpect(jsonPath("$.data.toolCalls[0].toolName").value("list_events"));
    }

    @Test
    void confirmationCanExecuteHighRiskDeleteFromChat() throws Exception {
        RegisteredUser user = register("ai_chat_delete_user", "ai-chat-delete-user@example.com", "AI Chat Delete");
        long eventId = createEvent(user, "确认删除目标");
        when(aiModelClient.chat(any()))
                .thenReturn(new AiModelResponse("test-model", "test", "", List.of(new AiRequestedToolCall(
                        "call_delete_event",
                        "delete_event",
                        Map.of("eventId", eventId)))))
                .thenReturn(new AiModelResponse("test-model", "test", "确认删除这个日程吗？", List.of()));

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "inputMode": "text",
                                  "message": "删除 %d"
                                }
                                """.formatted(user.personalSpaceId(), eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmations[0].actionType").value("delete_event"))
                .andExpect(jsonPath("$.data.toolCalls[0].status").value("confirmation_required"))
                .andReturn();
        long confirmationId = data(chatResult).path("confirmations").path(0).path("id").asLong();

        mockMvc.perform(post("/api/ai/confirmations/{confirmationId}/confirm", confirmationId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("confirmed"))
                .andExpect(jsonPath("$.data.resultCard.type").value("event_deleted"));

        Integer visible = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM calendar_events WHERE id = ? AND deleted_at IS NULL",
                Integer.class,
                eventId);
        MatcherAssert.assertThat(visible, Matchers.is(0));
    }

    @Test
    void chatReturnsAiServiceUnavailableWhenModelCallFails() throws Exception {
        RegisteredUser user = register("ai_chat_model_failed_user", "ai-chat-model-failed-user@example.com", "AI Chat Failed");
        when(aiModelClient.chat(any()))
                .thenThrow(new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "模型调用失败"));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "inputMode": "text",
                                  "message": "今天有什么安排？"
                                }
                                """.formatted(user.personalSpaceId())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_SERVICE_UNAVAILABLE"));
    }

    @Test
    void chatCanAnswerCasualConversationWithoutTools() throws Exception {
        RegisteredUser user = register("ai_chat_casual_user", "ai-chat-casual-user@example.com", "AI Chat Casual");
        when(aiModelClient.chat(any()))
                .thenReturn(new AiModelResponse("test-model", "test", "我是你的 AI 日历助手，也可以陪你聊聊天。", List.of()));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "inputMode": "text",
                                  "message": "你是什么模型？"
                                }
                                """.formatted(user.personalSpaceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("我是你的 AI 日历助手，也可以陪你聊聊天。"))
                .andExpect(jsonPath("$.data.toolCalls.length()").value(0));

        ArgumentCaptor<AiModelRequest> requestCaptor = ArgumentCaptor.forClass(AiModelRequest.class);
        verify(aiModelClient).chat(requestCaptor.capture());
        MatcherAssert.assertThat(requestCaptor.getValue().tools(), Matchers.empty());
    }

    private long createEvent(RegisteredUser user, String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/ai/tools/create_event/execute")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "arguments": {
                                    "title": "%s",
                                    "startTime": "2026-05-30T12:00:00+08:00",
                                    "endTime": "2026-05-30T13:00:00+08:00",
                                    "participantUserIds": [%d]
                                  }
                                }
                                """.formatted(user.personalSpaceId(), title, user.id())))
                .andExpect(status().isOk())
                .andReturn();
        return data(created).path("data").path("id").asLong();
    }

    private long createConversation(RegisteredUser user, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/conversations")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "calendarSpaceId": %d,
                                  "title": "%s",
                                  "channel": "text"
                                }
                                """.formatted(user.personalSpaceId(), title)))
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
