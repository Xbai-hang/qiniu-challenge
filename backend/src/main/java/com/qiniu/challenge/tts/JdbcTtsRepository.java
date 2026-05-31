package com.qiniu.challenge.tts;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTtsRepository implements TtsRepository {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TtsCacheEntry> rowMapper = (rs, rowNum) -> new TtsCacheEntry(
            rs.getLong("id"),
            rs.getLong("user_id"),
            nullableLong(rs, "message_id"),
            rs.getString("provider"),
            rs.getString("voice"),
            rs.getString("text_hash"),
            rs.getString("audio_url"),
            rs.getString("storage_key"),
            rs.getString("status"),
            toOffsetDateTime(rs.getTimestamp("expires_at")));

    public JdbcTtsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long create(CreateTtsCacheCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO tts_cache (
                      user_id,
                      message_id,
                      provider,
                      voice,
                      text_hash,
                      audio_url,
                      storage_key,
                      status,
                      expires_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, command.userId());
            if (command.messageId() == null) {
                statement.setObject(2, null);
            } else {
                statement.setLong(2, command.messageId());
            }
            statement.setString(3, command.provider());
            statement.setString(4, command.voice());
            statement.setString(5, command.textHash());
            statement.setString(6, command.audioUrl());
            statement.setString(7, command.storageKey());
            statement.setString(8, command.status());
            statement.setTimestamp(9, Timestamp.from(command.expiresAt().toInstant()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to create tts cache");
        }
        return key.longValue();
    }

    @Override
    public Optional<TtsCacheEntry> findActive(long id, long userId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM tts_cache
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'ready'
                  AND expires_at > CURRENT_TIMESTAMP
                """, rowMapper, id, userId).stream().findFirst();
    }

    @Override
    public boolean messageBelongsToUser(long messageId, long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ai_messages m
                JOIN ai_conversations c ON c.id = m.conversation_id
                WHERE m.id = ?
                  AND m.user_id = ?
                  AND c.user_id = ?
                  AND c.status = 'active'
                """, Integer.class, messageId, userId, userId);
        return count != null && count > 0;
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        return timestamp == null ? null : OffsetDateTime.ofInstant(timestamp.toInstant(), DEFAULT_ZONE);
    }
}
