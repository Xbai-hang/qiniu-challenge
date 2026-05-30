package com.qiniu.challenge.reminder;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
public class JdbcReminderRepository implements ReminderRepository {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    private final RowMapper<EventReminder> reminderRowMapper = (rs, rowNum) -> new EventReminder(
            rs.getLong("id"),
            rs.getLong("event_id"),
            rs.getLong("calendar_space_id"),
            rs.getLong("user_id"),
            nullableInt(rs, "offset_minutes"),
            toOffsetDateTime(rs.getTimestamp("trigger_at")),
            rs.getString("status"),
            nullableLong(rs, "snoozed_from_id"),
            rs.getLong("created_by"),
            toOffsetDateTime(rs.getTimestamp("created_at")),
            toOffsetDateTime(rs.getTimestamp("updated_at")),
            toOffsetDateTime(rs.getTimestamp("cancelled_at")));

    private final RowMapper<NotificationRecord> notificationRowMapper = (rs, rowNum) -> new NotificationRecord(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("calendar_space_id"),
            nullableLong(rs, "reminder_id"),
            rs.getString("type"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("payload"),
            rs.getString("status"),
            toOffsetDateTime(rs.getTimestamp("pushed_at")),
            toOffsetDateTime(rs.getTimestamp("read_at")),
            toOffsetDateTime(rs.getTimestamp("created_at")));

    public JdbcReminderRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    @Override
    public long createReminder(CreateReminderCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO event_reminders (
                      event_id,
                      calendar_space_id,
                      user_id,
                      offset_minutes,
                      trigger_at,
                      status,
                      snoozed_from_id,
                      created_by
                    )
                    VALUES (?, ?, ?, ?, ?, 'pending', ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, command.eventId());
            statement.setLong(2, command.calendarSpaceId());
            statement.setLong(3, command.userId());
            setNullableInt(statement, 4, command.offsetMinutes());
            statement.setTimestamp(5, toTimestamp(command.triggerAt()));
            setNullableLong(statement, 6, command.snoozedFromId());
            statement.setLong(7, command.createdBy());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to read generated reminder id");
        }
        return key.longValue();
    }

    @Override
    public Optional<EventReminder> findReminder(long reminderId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM event_reminders
                WHERE id = ?
                """, reminderRowMapper, reminderId).stream().findFirst();
    }

    @Override
    public List<EventReminder> findRemindersByEvent(long eventId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM event_reminders
                WHERE event_id = ?
                ORDER BY trigger_at ASC, id ASC
                """, reminderRowMapper, eventId);
    }

    @Override
    public boolean updateReminder(long reminderId, Integer offsetMinutes, OffsetDateTime triggerAt) {
        int updated = jdbcTemplate.update("""
                UPDATE event_reminders
                SET offset_minutes = ?,
                    trigger_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status IN ('pending', 'sent', 'read', 'snoozed')
                """, offsetMinutes, toTimestamp(triggerAt), reminderId);
        return updated > 0;
    }

    @Override
    public boolean cancelReminder(long reminderId) {
        int updated = jdbcTemplate.update("""
                UPDATE event_reminders
                SET status = 'cancelled',
                    cancelled_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status <> 'cancelled'
                """, reminderId);
        return updated > 0;
    }

    @Override
    public boolean markReminderSent(long reminderId) {
        int updated = jdbcTemplate.update("""
                UPDATE event_reminders
                SET status = 'sent',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'pending'
                """, reminderId);
        return updated > 0;
    }

    @Override
    public boolean markReminderSnoozed(long reminderId) {
        int updated = jdbcTemplate.update("""
                UPDATE event_reminders
                SET status = 'snoozed',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status <> 'cancelled'
                """, reminderId);
        return updated > 0;
    }

    @Override
    public List<EventReminder> findDueReminders(OffsetDateTime now, int limit) {
        return namedJdbcTemplate.query("""
                SELECT *
                FROM event_reminders
                WHERE status = 'pending'
                  AND trigger_at <= :now
                ORDER BY trigger_at ASC, id ASC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("now", toTimestamp(now))
                .addValue("limit", limit), reminderRowMapper);
    }

    @Override
    public long createNotification(CreateNotificationCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO notifications (
                      user_id,
                      calendar_space_id,
                      reminder_id,
                      type,
                      title,
                      content,
                      payload,
                      status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'unread')
                    """, new String[]{"id"});
            statement.setLong(1, command.userId());
            statement.setLong(2, command.calendarSpaceId());
            setNullableLong(statement, 3, command.reminderId());
            statement.setString(4, command.type());
            statement.setString(5, command.title());
            statement.setString(6, command.content());
            statement.setString(7, command.payload());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to read generated notification id");
        }
        return key.longValue();
    }

    @Override
    public Optional<NotificationRecord> findNotification(long notificationId, long userId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM notifications
                WHERE id = ?
                  AND user_id = ?
                """, notificationRowMapper, notificationId, userId).stream().findFirst();
    }

    @Override
    public List<NotificationRecord> findNotifications(long userId, String status, int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM notifications
                WHERE user_id = :userId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status.trim());
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset");
        return namedJdbcTemplate.query(sql.toString(), params, notificationRowMapper);
    }

    @Override
    public long countNotifications(long userId, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                FROM notifications
                WHERE user_id = :userId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status.trim());
        }
        Long count = namedJdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public long countUnreadNotifications(long userId) {
        return countNotifications(userId, "unread");
    }

    @Override
    public boolean markNotificationRead(long notificationId, long userId) {
        int updated = jdbcTemplate.update("""
                UPDATE notifications
                SET status = 'read',
                    read_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'unread'
                """, notificationId, userId);
        return updated > 0;
    }

    @Override
    public boolean markNotificationPushed(long notificationId, OffsetDateTime pushedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE notifications
                SET pushed_at = ?
                WHERE id = ?
                """, toTimestamp(pushedAt), notificationId);
        return updated > 0;
    }

    @Override
    public OffsetDateTime now() {
        return OffsetDateTime.now(DEFAULT_ZONE);
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
