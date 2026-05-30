package com.qiniu.challenge.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiRepository implements AiRepository {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<AiConversation> conversationRowMapper = (rs, rowNum) -> new AiConversation(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("calendar_space_id"),
            rs.getString("title"),
            rs.getString("channel"),
            rs.getString("ai_prompt_version"),
            rs.getString("tool_schema_version"),
            rs.getString("model_provider"),
            rs.getString("model_name"),
            rs.getString("status"),
            toOffsetDateTime(rs.getTimestamp("created_at")),
            toOffsetDateTime(rs.getTimestamp("updated_at")));

    private final RowMapper<AiMessage> messageRowMapper = (rs, rowNum) -> new AiMessage(
            rs.getLong("id"),
            rs.getLong("conversation_id"),
            rs.getLong("user_id"),
            rs.getString("role"),
            rs.getString("input_mode"),
            rs.getString("content"),
            rs.getString("structured_payload"),
            nullableLong(rs, "transcription_id"),
            rs.getString("ai_prompt_version"),
            rs.getString("tool_schema_version"),
            rs.getString("model_provider"),
            rs.getString("model_name"),
            toOffsetDateTime(rs.getTimestamp("created_at")));

    private final RowMapper<AiToolCallLogRecord> toolCallLogRowMapper = (rs, rowNum) -> new AiToolCallLogRecord(
            rs.getLong("id"),
            rs.getLong("conversation_id"),
            nullableLong(rs, "message_id"),
            rs.getLong("user_id"),
            rs.getLong("calendar_space_id"),
            rs.getString("tool_name"),
            rs.getString("risk_level"),
            rs.getString("required_permission"),
            rs.getString("input_payload"),
            rs.getString("output_payload"),
            rs.getString("status"),
            rs.getString("error_code"),
            rs.getString("error_message"),
            toOffsetDateTime(rs.getTimestamp("started_at")),
            toOffsetDateTime(rs.getTimestamp("finished_at")));

    private final RowMapper<AiTaskStateResponse> taskStateRowMapper = (rs, rowNum) -> new AiTaskStateResponse(
            rs.getLong("id"),
            rs.getLong("conversation_id"),
            rs.getLong("calendar_space_id"),
            rs.getString("task_type"),
            rs.getString("status"),
            readObjectMap(rs.getString("draft_payload")),
            readStringList(rs.getString("missing_fields")),
            rs.getString("risk_level"),
            toOffsetDateTime(rs.getTimestamp("expires_at")));

    private final RowMapper<PendingConfirmationResponse> confirmationRowMapper = (rs, rowNum) -> new PendingConfirmationResponse(
            rs.getLong("id"),
            rs.getLong("conversation_id"),
            rs.getLong("calendar_space_id"),
            rs.getString("action_type"),
            rs.getString("risk_level"),
            rs.getString("summary"),
            readObjectMap(rs.getString("payload")),
            rs.getString("status"),
            toOffsetDateTime(rs.getTimestamp("expires_at")));

    public JdbcAiRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public long createConversation(CreateAiConversationCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO ai_conversations (
                      user_id,
                      calendar_space_id,
                      title,
                      channel,
                      ai_prompt_version,
                      tool_schema_version,
                      model_provider,
                      model_name,
                      status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active')
                    """, new String[]{"id"});
            statement.setLong(1, command.userId());
            statement.setLong(2, command.calendarSpaceId());
            statement.setString(3, command.title());
            statement.setString(4, command.channel());
            statement.setString(5, command.aiPromptVersion());
            statement.setString(6, command.toolSchemaVersion());
            statement.setString(7, command.modelProvider());
            statement.setString(8, command.modelName());
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "conversation");
    }

    @Override
    public Optional<AiConversation> findConversation(long conversationId, long userId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM ai_conversations
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'active'
                """, conversationRowMapper, conversationId, userId).stream().findFirst();
    }

    @Override
    public List<AiConversation> findConversations(long userId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM ai_conversations
                WHERE user_id = ?
                ORDER BY updated_at DESC, id DESC
                """, conversationRowMapper, userId);
    }

    @Override
    public long createMessage(CreateAiMessageCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO ai_messages (
                      conversation_id,
                      user_id,
                      role,
                      input_mode,
                      content,
                      structured_payload,
                      transcription_id,
                      ai_prompt_version,
                      tool_schema_version,
                      model_provider,
                      model_name
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, command.conversationId());
            statement.setLong(2, command.userId());
            statement.setString(3, command.role());
            statement.setString(4, command.inputMode());
            statement.setString(5, command.content());
            statement.setString(6, toJson(command.structuredPayload()));
            setNullableLong(statement, 7, command.transcriptionId());
            statement.setString(8, command.aiPromptVersion());
            statement.setString(9, command.toolSchemaVersion());
            statement.setString(10, command.modelProvider());
            statement.setString(11, command.modelName());
            return statement;
        }, keyHolder);
        jdbcTemplate.update("UPDATE ai_conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                command.conversationId());
        return generatedId(keyHolder, "message");
    }

    @Override
    public List<AiMessage> findMessages(long conversationId, long userId) {
        return jdbcTemplate.query("""
                SELECT m.*
                FROM ai_messages m
                JOIN ai_conversations c ON c.id = m.conversation_id
                WHERE m.conversation_id = ?
                  AND c.user_id = ?
                ORDER BY m.created_at ASC, m.id ASC
                """, messageRowMapper, conversationId, userId);
    }

    @Override
    public long startToolCall(AiToolCallLogEntry entry) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO ai_tool_call_logs (
                      conversation_id,
                      message_id,
                      user_id,
                      calendar_space_id,
                      tool_name,
                      risk_level,
                      required_permission,
                      input_payload,
                      status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'started')
                    """, new String[]{"id"});
            statement.setLong(1, entry.conversationId());
            setNullableLong(statement, 2, entry.messageId());
            statement.setLong(3, entry.userId());
            statement.setLong(4, entry.calendarSpaceId());
            statement.setString(5, entry.toolName());
            statement.setString(6, entry.riskLevel().value());
            statement.setString(7, entry.requiredPermission());
            statement.setString(8, toJson(entry.inputPayload()));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "tool call log");
    }

    @Override
    public void finishToolCall(long logId, String status, Object outputPayload, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE ai_tool_call_logs
                SET status = ?,
                    output_payload = ?,
                    error_code = ?,
                    error_message = ?,
                    finished_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status, toJson(outputPayload), errorCode, errorMessage, logId);
    }

    @Override
    public AiToolCallLogPage findToolCallLogs(
            long currentUserId,
            Long conversationId,
            Long calendarSpaceId,
            int page,
            int size) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<Object> countParams = new ArrayList<>();
        countParams.add(currentUserId);
        String where = toolLogWhere(conversationId, calendarSpaceId, countParams);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM ai_tool_call_logs l
                JOIN calendar_spaces s ON s.id = l.calendar_space_id
                LEFT JOIN organization_members m
                  ON m.organization_id = s.organization_id
                 AND m.user_id = ?
                 AND m.status = 'active'
                """ + where, Long.class, countParams.toArray());

        List<Object> listParams = new ArrayList<>();
        listParams.add(currentUserId);
        String listWhere = toolLogWhere(conversationId, calendarSpaceId, listParams);
        listParams.add(normalizedSize);
        listParams.add((normalizedPage - 1) * normalizedSize);
        List<AiToolCallLogRecord> items = jdbcTemplate.query("""
                SELECT l.*
                FROM ai_tool_call_logs l
                JOIN calendar_spaces s ON s.id = l.calendar_space_id
                LEFT JOIN organization_members m
                  ON m.organization_id = s.organization_id
                 AND m.user_id = ?
                 AND m.status = 'active'
                """ + listWhere + """
                ORDER BY l.started_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, toolCallLogRowMapper, listParams.toArray());
        return new AiToolCallLogPage(items, normalizedPage, normalizedSize, total == null ? 0 : total);
    }

    @Override
    public long createTaskState(CreateAiTaskStateCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO ai_task_states (
                      conversation_id,
                      user_id,
                      calendar_space_id,
                      task_type,
                      status,
                      draft_payload,
                      missing_fields,
                      risk_level,
                      expires_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, command.conversationId());
            statement.setLong(2, command.userId());
            statement.setLong(3, command.calendarSpaceId());
            statement.setString(4, command.taskType());
            statement.setString(5, command.status());
            statement.setString(6, toJson(command.draftPayload()));
            statement.setString(7, toJson(command.missingFields()));
            statement.setString(8, command.riskLevel());
            statement.setTimestamp(9, toTimestamp(command.expiresAt()));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "task state");
    }

    @Override
    public List<AiTaskStateResponse> findOpenTaskStates(long userId, long conversationId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM ai_task_states
                WHERE user_id = ?
                  AND conversation_id = ?
                  AND status IN ('collecting_info', 'awaiting_confirmation', 'executing')
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                ORDER BY updated_at DESC, id DESC
                """, taskStateRowMapper, userId, conversationId);
    }

    @Override
    public long createPendingConfirmation(CreatePendingConfirmationCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO pending_confirmations (
                      conversation_id,
                      user_id,
                      calendar_space_id,
                      action_type,
                      risk_level,
                      summary,
                      payload,
                      expires_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, command.conversationId());
            statement.setLong(2, command.userId());
            statement.setLong(3, command.calendarSpaceId());
            statement.setString(4, command.actionType());
            statement.setString(5, command.riskLevel());
            statement.setString(6, command.summary());
            statement.setString(7, toJson(command.payload()));
            statement.setTimestamp(8, toTimestamp(command.expiresAt()));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "pending confirmation");
    }

    @Override
    public Optional<PendingConfirmationResponse> findPendingConfirmation(long confirmationId, long userId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM pending_confirmations
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'pending'
                  AND expires_at > CURRENT_TIMESTAMP
                """, confirmationRowMapper, confirmationId, userId).stream().findFirst();
    }

    @Override
    public List<PendingConfirmationResponse> findPendingConfirmations(long userId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM pending_confirmations
                WHERE user_id = ?
                  AND status = 'pending'
                  AND expires_at > CURRENT_TIMESTAMP
                ORDER BY created_at DESC, id DESC
                """, confirmationRowMapper, userId);
    }

    @Override
    public void markConfirmation(long confirmationId, String status) {
        jdbcTemplate.update("""
                UPDATE pending_confirmations
                SET status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status, confirmationId);
    }

    private String toolLogWhere(Long conversationId, Long calendarSpaceId, List<Object> params) {
        StringBuilder sql = new StringBuilder("""
                WHERE s.deleted_at IS NULL
                  AND (
                    (s.type = 'personal' AND s.owner_user_id = ?)
                    OR
                    (s.type = 'organization' AND m.user_id IS NOT NULL)
                  )
                """);
        params.add(params.get(0));
        if (conversationId != null) {
            sql.append(" AND l.conversation_id = ?");
            params.add(conversationId);
        }
        if (calendarSpaceId != null) {
            sql.append(" AND l.calendar_space_id = ?");
            params.add(calendarSpaceId);
        }
        return sql.toString();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid AI payload", exception);
        }
    }

    private Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(json, OBJECT_MAP);
            return value == null ? Map.of() : new LinkedHashMap<>(value);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> value = objectMapper.readValue(json, STRING_LIST);
            return value == null ? List.of() : value;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private long generatedId(KeyHolder keyHolder, String target) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to read generated " + target + " id");
        }
        return key.longValue();
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().atZone(DEFAULT_ZONE).toOffsetDateTime();
    }

    private static Timestamp toTimestamp(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return Timestamp.valueOf(value.atZoneSameInstant(DEFAULT_ZONE).toLocalDateTime());
    }
}
