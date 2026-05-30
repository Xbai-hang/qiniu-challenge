package com.qiniu.challenge.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOperationLogRepository implements OperationLogRepository {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_EXPORT_SIZE = 5000;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<OperationLogRecord> logRowMapper = (rs, rowNum) -> new OperationLogRecord(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("user_display_name"),
            rs.getLong("calendar_space_id"),
            rs.getString("calendar_space_name"),
            rs.getString("operation_source"),
            rs.getString("operation_type"),
            rs.getString("target_type"),
            nullableLong(rs, "target_id"),
            rs.getString("before_snapshot"),
            rs.getString("after_snapshot"),
            rs.getBoolean("undoable"),
            rs.getBoolean("undone"),
            toOffsetDateTime(rs.getTimestamp("undo_expires_at")),
            toOffsetDateTime(rs.getTimestamp("created_at")));

    public JdbcOperationLogRepository(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public long create(OperationLogEntry entry) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO operation_logs (
                      user_id,
                      calendar_space_id,
                      conversation_id,
                      tool_call_id,
                      operation_source,
                      operation_type,
                      target_type,
                      target_id,
                      before_snapshot,
                      after_snapshot,
                      undoable,
                      undo_expires_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, entry.userId());
            statement.setLong(2, entry.calendarSpaceId());
            setNullableLong(statement, 3, entry.conversationId());
            setNullableLong(statement, 4, entry.toolCallId());
            statement.setString(5, entry.operationSource());
            statement.setString(6, entry.operationType());
            statement.setString(7, entry.targetType());
            setNullableLong(statement, 8, entry.targetId());
            statement.setString(9, toJson(entry.beforeSnapshot()));
            statement.setString(10, toJson(entry.afterSnapshot()));
            statement.setBoolean(11, entry.undoable());
            statement.setTimestamp(12, toTimestamp(entry.undoExpiresAt()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to read generated operation log id");
        }
        return key.longValue();
    }

    @Override
    public OperationLogPage findLogs(OperationLogQuery query) {
        List<Object> countParams = new ArrayList<>();
        countParams.add(query.currentUserId());
        String where = whereClause(query, countParams);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM operation_logs l
                JOIN users u ON u.id = l.user_id
                JOIN calendar_spaces s ON s.id = l.calendar_space_id
                LEFT JOIN organization_members m
                  ON m.organization_id = s.organization_id
                 AND m.user_id = ?
                 AND m.status = 'active'
                """ + where, Long.class, countParams.toArray());

        List<Object> listParams = new ArrayList<>();
        listParams.add(query.currentUserId());
        String listWhere = whereClause(query, listParams);
        listParams.add(query.size());
        listParams.add(query.offset());
        List<OperationLogRecord> items = jdbcTemplate.query("""
                SELECT
                  l.id,
                  l.user_id,
                  u.display_name AS user_display_name,
                  l.calendar_space_id,
                  s.name AS calendar_space_name,
                  l.operation_source,
                  l.operation_type,
                  l.target_type,
                  l.target_id,
                  l.before_snapshot,
                  l.after_snapshot,
                  l.undoable,
                  l.undone,
                  l.undo_expires_at,
                  l.created_at
                FROM operation_logs l
                JOIN users u ON u.id = l.user_id
                JOIN calendar_spaces s ON s.id = l.calendar_space_id
                LEFT JOIN organization_members m
                  ON m.organization_id = s.organization_id
                 AND m.user_id = ?
                 AND m.status = 'active'
                """ + listWhere + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, logRowMapper, listParams.toArray());
        return new OperationLogPage(items, query.page(), query.size(), total == null ? 0 : total);
    }

    @Override
    public List<OperationLogRecord> findLogsForExport(OperationLogQuery query) {
        List<Object> params = new ArrayList<>();
        params.add(query.currentUserId());
        String where = whereClause(query, params);
        params.add(MAX_EXPORT_SIZE);
        return jdbcTemplate.query("""
                SELECT
                  l.id,
                  l.user_id,
                  u.display_name AS user_display_name,
                  l.calendar_space_id,
                  s.name AS calendar_space_name,
                  l.operation_source,
                  l.operation_type,
                  l.target_type,
                  l.target_id,
                  l.before_snapshot,
                  l.after_snapshot,
                  l.undoable,
                  l.undone,
                  l.undo_expires_at,
                  l.created_at
                FROM operation_logs l
                JOIN users u ON u.id = l.user_id
                JOIN calendar_spaces s ON s.id = l.calendar_space_id
                LEFT JOIN organization_members m
                  ON m.organization_id = s.organization_id
                 AND m.user_id = ?
                 AND m.status = 'active'
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ?
                """, logRowMapper, params.toArray());
    }

    @Override
    public Optional<OperationLogRecord> findLastUndoableAiOperation(long userId, long calendarSpaceId) {
        return jdbcTemplate.query("""
                SELECT
                  l.id,
                  l.user_id,
                  u.display_name AS user_display_name,
                  l.calendar_space_id,
                  s.name AS calendar_space_name,
                  l.operation_source,
                  l.operation_type,
                  l.target_type,
                  l.target_id,
                  l.before_snapshot,
                  l.after_snapshot,
                  l.undoable,
                  l.undone,
                  l.undo_expires_at,
                  l.created_at
                FROM operation_logs l
                JOIN users u ON u.id = l.user_id
                JOIN calendar_spaces s ON s.id = l.calendar_space_id
                WHERE l.user_id = ?
                  AND l.calendar_space_id = ?
                  AND l.operation_source = 'ai'
                  AND l.undoable = TRUE
                  AND l.undone = FALSE
                  AND l.undo_expires_at > CURRENT_TIMESTAMP
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 1
                """, logRowMapper, userId, calendarSpaceId).stream().findFirst();
    }

    @Override
    public void markUndone(long operationId) {
        jdbcTemplate.update("""
                UPDATE operation_logs
                SET undone = TRUE
                WHERE id = ?
                """, operationId);
    }

    private String whereClause(OperationLogQuery query, List<Object> params) {
        StringBuilder sql = new StringBuilder("""
                WHERE u.deleted_at IS NULL
                  AND s.deleted_at IS NULL
                """);
        if (query.calendarSpaceId() != null) {
            sql.append(" AND l.calendar_space_id = ?");
            params.add(query.calendarSpaceId());
        } else {
            sql.append("""
                     AND (
                       (s.type = 'personal' AND s.owner_user_id = ? AND l.user_id = ?)
                       OR
                       (s.type = 'organization' AND m.role IN ('owner', 'admin'))
                     )
                    """);
            params.add(query.currentUserId());
            params.add(query.currentUserId());
        }
        if (hasText(query.operationSource())) {
            sql.append(" AND l.operation_source = ?");
            params.add(query.operationSource().trim());
        }
        if (hasText(query.targetType())) {
            sql.append(" AND l.target_type = ?");
            params.add(query.targetType().trim());
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
            throw new IllegalArgumentException("Invalid snapshot value", exception);
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
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

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
