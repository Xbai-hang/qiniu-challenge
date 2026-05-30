package com.qiniu.challenge.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventRepository implements EventRepository {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<CalendarEvent> eventRowMapper = (rs, rowNum) -> new CalendarEvent(
            rs.getLong("id"),
            rs.getLong("calendar_space_id"),
            nullableLong(rs, "organization_id"),
            rs.getLong("created_by"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("location"),
            toOffsetDateTime(rs.getTimestamp("start_time")),
            toOffsetDateTime(rs.getTimestamp("end_time")),
            rs.getString("timezone"),
            rs.getBoolean("all_day"),
            rs.getString("visibility"),
            rs.getString("source"),
            rs.getString("repeat_type"),
            toOffsetDateTime(rs.getTimestamp("repeat_until")),
            nullableInt(rs, "repeat_count"),
            rs.getString("repeat_rule_text"),
            rs.getString("project"),
            nullableLong(rs, "owner_user_id"),
            rs.getString("status"),
            rs.getString("priority"),
            readTags(rs.getString("tags")),
            rs.getString("event_type"),
            rs.getString("notes"),
            rs.getString("custom_fields"),
            rs.getInt("version"));

    private final RowMapper<EventParticipant> participantRowMapper = (rs, rowNum) -> new EventParticipant(
            rs.getLong("user_id"),
            rs.getString("display_name"),
            rs.getString("role"),
            rs.getString("response_status"));

    private final RowMapper<EventConflict> conflictRowMapper = (rs, rowNum) -> new EventConflict(
            rs.getLong("event_id"),
            rs.getLong("calendar_space_id"),
            rs.getString("calendar_space_name"),
            "已有安排",
            rs.getLong("participant_user_id"),
            rs.getString("participant_name"),
            toOffsetDateTime(rs.getTimestamp("start_time")),
            toOffsetDateTime(rs.getTimestamp("end_time")));

    public JdbcEventRepository(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CalendarSpaceAccess> findAccessibleSpace(long spaceId, long userId) {
        return jdbcTemplate.query("""
                SELECT id, type, name, owner_user_id, organization_id, role
                FROM (
                  SELECT
                    s.id,
                    s.type,
                    s.name,
                    s.owner_user_id,
                    s.organization_id,
                    'owner' AS role
                  FROM calendar_spaces s
                  WHERE s.id = ?
                    AND s.type = 'personal'
                    AND s.owner_user_id = ?
                    AND s.deleted_at IS NULL

                  UNION ALL

                  SELECT
                    s.id,
                    s.type,
                    s.name,
                    s.owner_user_id,
                    s.organization_id,
                    m.role
                  FROM calendar_spaces s
                  JOIN organization_members m ON m.organization_id = s.organization_id
                  JOIN organizations o ON o.id = s.organization_id
                  WHERE s.id = ?
                    AND s.type = 'organization'
                    AND m.user_id = ?
                    AND m.status = 'active'
                    AND o.status = 'active'
                    AND s.deleted_at IS NULL
                    AND o.deleted_at IS NULL
                ) accessible_space
                """, (rs, rowNum) -> new CalendarSpaceAccess(
                rs.getLong("id"),
                rs.getString("type"),
                rs.getString("name"),
                nullableLong(rs, "owner_user_id"),
                nullableLong(rs, "organization_id"),
                rs.getString("role")), spaceId, userId, spaceId, userId).stream().findFirst();
    }

    @Override
    public boolean areActiveOrganizationMembers(long organizationId, List<Long> userIds) {
        if (userIds.isEmpty()) {
            return true;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("userIds", userIds);
        Integer count = namedJdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT user_id)
                FROM organization_members
                WHERE organization_id = :organizationId
                  AND user_id IN (:userIds)
                  AND status = 'active'
                """, params, Integer.class);
        return count != null && count == userIds.size();
    }

    @Override
    public long createEvent(CalendarEvent event) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO calendar_events (
                      calendar_space_id,
                      organization_id,
                      created_by,
                      title,
                      description,
                      location,
                      start_time,
                      end_time,
                      timezone,
                      all_day,
                      visibility,
                      source,
                      repeat_type,
                      repeat_until,
                      repeat_count,
                      repeat_rule_text,
                      project,
                      owner_user_id,
                      status,
                      priority,
                      tags,
                      event_type,
                      notes,
                      custom_fields
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            bindEventForInsert(statement, event);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to read generated event id");
        }
        return key.longValue();
    }

    @Override
    public Optional<CalendarEvent> findEvent(long eventId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM calendar_events
                WHERE id = ?
                  AND deleted_at IS NULL
                """, eventRowMapper, eventId).stream().findFirst();
    }

    @Override
    public List<CalendarEvent> findEvents(EventSearchRequest request, long currentUserId) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.*
                FROM calendar_events e
                JOIN calendar_spaces s ON s.id = e.calendar_space_id
                LEFT JOIN organization_members m
                  ON m.organization_id = s.organization_id
                 AND m.user_id = :currentUserId
                 AND m.status = 'active'
                WHERE e.deleted_at IS NULL
                  AND s.deleted_at IS NULL
                  AND (
                    (s.type = 'personal' AND s.owner_user_id = :currentUserId)
                    OR
                    (s.type = 'organization' AND m.user_id IS NOT NULL)
                  )
                  AND (
                    e.visibility = 'space'
                    OR e.created_by = :currentUserId
                    OR EXISTS (
                      SELECT 1
                      FROM event_participants ep
                      WHERE ep.event_id = e.id
                        AND ep.user_id = :currentUserId
                    )
                  )
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("currentUserId", currentUserId);

        if (request.calendarSpaceId() != null) {
            sql.append(" AND e.calendar_space_id = :calendarSpaceId");
            params.addValue("calendarSpaceId", request.calendarSpaceId());
        }
        if (request.start() != null) {
            sql.append(" AND e.end_time > :start");
            params.addValue("start", toTimestamp(request.start()));
        }
        if (request.end() != null) {
            sql.append(" AND e.start_time < :end");
            params.addValue("end", toTimestamp(request.end()));
        }
        if (hasText(request.keyword())) {
            sql.append("""
                     AND (
                       LOWER(e.title) LIKE :keyword
                       OR LOWER(COALESCE(e.description, '')) LIKE :keyword
                       OR LOWER(COALESCE(e.notes, '')) LIKE :keyword
                       OR LOWER(COALESCE(e.project, '')) LIKE :keyword
                       OR LOWER(COALESCE(e.event_type, '')) LIKE :keyword
                       OR LOWER(COALESCE(e.tags, '')) LIKE :keyword
                     )
                    """);
            params.addValue("keyword", "%" + request.keyword().trim().toLowerCase() + "%");
        }
        if (hasText(request.project())) {
            sql.append(" AND e.project = :project");
            params.addValue("project", request.project().trim());
        }
        if (request.ownerUserId() != null) {
            sql.append(" AND e.owner_user_id = :ownerUserId");
            params.addValue("ownerUserId", request.ownerUserId());
        }
        if (hasText(request.status())) {
            sql.append(" AND e.status = :status");
            params.addValue("status", request.status().trim());
        }
        if (hasText(request.priority())) {
            sql.append(" AND e.priority = :priority");
            params.addValue("priority", request.priority().trim());
        }
        if (hasText(request.tag())) {
            sql.append(" AND LOWER(COALESCE(e.tags, '')) LIKE :tag");
            params.addValue("tag", "%\"" + request.tag().trim().toLowerCase() + "\"%");
        }

        sql.append(" ORDER BY ").append(sortColumn(request.sortBy())).append(sortDirection(request.sortDirection()))
                .append(", e.id ASC");
        return namedJdbcTemplate.query(sql.toString(), params, eventRowMapper);
    }

    @Override
    public List<EventConflict> findConflicts(
            long currentUserId,
            List<Long> participantUserIds,
            OffsetDateTime start,
            OffsetDateTime end,
            Long excludeEventId) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                  0 AS event_id,
                  0 AS calendar_space_id,
                  NULL AS calendar_space_name,
                  ep.user_id AS participant_user_id,
                  u.display_name AS participant_name,
                  e.start_time,
                  e.end_time
                FROM calendar_events e
                JOIN calendar_spaces s ON s.id = e.calendar_space_id
                JOIN event_participants ep ON ep.event_id = e.id
                JOIN users u ON u.id = ep.user_id
                WHERE e.deleted_at IS NULL
                  AND s.deleted_at IS NULL
                  AND u.deleted_at IS NULL
                  AND ep.user_id IN (:participantUserIds)
                  AND e.end_time > :start
                  AND e.start_time < :end
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("currentUserId", currentUserId)
                .addValue("participantUserIds", participantUserIds)
                .addValue("start", toTimestamp(start))
                .addValue("end", toTimestamp(end));
        if (excludeEventId != null) {
            sql.append(" AND e.id <> :excludeEventId");
            params.addValue("excludeEventId", excludeEventId);
        }
        sql.append(" ORDER BY e.start_time ASC, e.id ASC, ep.user_id ASC");
        return namedJdbcTemplate.query(sql.toString(), params, conflictRowMapper);
    }

    @Override
    public boolean updateEvent(CalendarEvent event, int expectedVersion) {
        int updated = jdbcTemplate.update("""
                UPDATE calendar_events
                SET title = ?,
                    description = ?,
                    location = ?,
                    start_time = ?,
                    end_time = ?,
                    timezone = ?,
                    all_day = ?,
                    visibility = ?,
                    repeat_type = ?,
                    repeat_until = ?,
                    repeat_count = ?,
                    repeat_rule_text = ?,
                    project = ?,
                    owner_user_id = ?,
                    status = ?,
                    priority = ?,
                    tags = ?,
                    event_type = ?,
                    notes = ?,
                    custom_fields = ?,
                    version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND version = ?
                  AND deleted_at IS NULL
                """,
                event.title(),
                event.description(),
                event.location(),
                toTimestamp(event.startTime()),
                toTimestamp(event.endTime()),
                event.timezone(),
                event.allDay(),
                event.visibility(),
                event.repeatType(),
                toTimestamp(event.repeatUntil()),
                event.repeatCount(),
                event.repeatRuleText(),
                event.project(),
                event.ownerUserId(),
                event.status(),
                event.priority(),
                toJson(event.tags()),
                event.eventType(),
                event.notes(),
                event.customFields(),
                event.id(),
                expectedVersion);
        return updated > 0;
    }

    @Override
    public boolean softDeleteEvent(long eventId) {
        int updated = jdbcTemplate.update("""
                UPDATE calendar_events
                SET deleted_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = ?
                  AND deleted_at IS NULL
                """, eventId);
        return updated > 0;
    }

    @Override
    public List<EventParticipant> findParticipants(long eventId) {
        return jdbcTemplate.query("""
                SELECT
                  ep.user_id,
                  u.display_name,
                  ep.role,
                  ep.response_status
                FROM event_participants ep
                JOIN users u ON u.id = ep.user_id
                WHERE ep.event_id = ?
                  AND u.deleted_at IS NULL
                ORDER BY
                  CASE ep.role WHEN 'organizer' THEN 0 ELSE 1 END,
                  ep.user_id
                """, participantRowMapper, eventId);
    }

    @Override
    public void replaceParticipants(long eventId, List<ParticipantCommand> participants) {
        jdbcTemplate.update("DELETE FROM event_participants WHERE event_id = ?", eventId);
        for (ParticipantCommand participant : participants) {
            jdbcTemplate.update("""
                    INSERT INTO event_participants (event_id, user_id, role, response_status)
                    VALUES (?, ?, ?, ?)
                    """, eventId, participant.userId(), participant.role(), participant.responseStatus());
        }
    }

    @Override
    public boolean isParticipant(long eventId, long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM event_participants
                WHERE event_id = ?
                  AND user_id = ?
                """, Integer.class, eventId, userId);
        return count != null && count > 0;
    }

    @Override
    public OffsetDateTime now() {
        return OffsetDateTime.now(DEFAULT_ZONE);
    }

    private void bindEventForInsert(PreparedStatement statement, CalendarEvent event) throws java.sql.SQLException {
        statement.setLong(1, event.calendarSpaceId());
        setNullableLong(statement, 2, event.organizationId());
        statement.setLong(3, event.createdBy());
        statement.setString(4, event.title());
        statement.setString(5, event.description());
        statement.setString(6, event.location());
        statement.setTimestamp(7, toTimestamp(event.startTime()));
        statement.setTimestamp(8, toTimestamp(event.endTime()));
        statement.setString(9, event.timezone());
        statement.setBoolean(10, event.allDay());
        statement.setString(11, event.visibility());
        statement.setString(12, event.source());
        statement.setString(13, event.repeatType());
        statement.setTimestamp(14, toTimestamp(event.repeatUntil()));
        setNullableInt(statement, 15, event.repeatCount());
        statement.setString(16, event.repeatRuleText());
        statement.setString(17, event.project());
        setNullableLong(statement, 18, event.ownerUserId());
        statement.setString(19, event.status());
        statement.setString(20, event.priority());
        statement.setString(21, toJson(event.tags()));
        statement.setString(22, event.eventType());
        statement.setString(23, event.notes());
        statement.setString(24, event.customFields());
    }

    private String sortColumn(String sortBy) {
        if (!hasText(sortBy)) {
            return "e.start_time";
        }
        return switch (sortBy.trim()) {
            case "startTime" -> "e.start_time";
            case "endTime" -> "e.end_time";
            case "title" -> "e.title";
            case "project" -> "e.project";
            case "ownerUserId" -> "e.owner_user_id";
            case "status" -> "e.status";
            case "priority" -> "e.priority";
            case "createdAt" -> "e.created_at";
            case "updatedAt" -> "e.updated_at";
            default -> "e.start_time";
        };
    }

    private String sortDirection(String sortDirection) {
        if ("desc".equalsIgnoreCase(sortDirection)) {
            return " DESC";
        }
        return " ASC";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String toJson(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON value", exception);
        }
    }

    private List<String> readTags(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(json, STRING_LIST);
            return tags == null ? List.of() : tags;
        } catch (JsonProcessingException exception) {
            return new ArrayList<>();
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

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }
}
