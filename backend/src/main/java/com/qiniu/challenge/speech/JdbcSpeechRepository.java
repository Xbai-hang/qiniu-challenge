package com.qiniu.challenge.speech;

import java.sql.PreparedStatement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSpeechRepository implements SpeechRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSpeechRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long create(CreateSpeechTranscriptionCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO speech_transcriptions (
                      user_id,
                      calendar_space_id,
                      conversation_id,
                      provider,
                      model_name,
                      transcript_text,
                      confidence,
                      audio_format,
                      audio_duration_ms,
                      status,
                      error_code,
                      error_message
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, command.userId());
            statement.setLong(2, command.calendarSpaceId());
            if (command.conversationId() == null) {
                statement.setObject(3, null);
            } else {
                statement.setLong(3, command.conversationId());
            }
            statement.setString(4, command.provider());
            statement.setString(5, command.modelName());
            statement.setString(6, command.transcriptText());
            statement.setBigDecimal(7, command.confidence());
            statement.setString(8, command.audioFormat());
            if (command.audioDurationMs() == null) {
                statement.setObject(9, null);
            } else {
                statement.setInt(9, command.audioDurationMs());
            }
            statement.setString(10, command.status());
            statement.setString(11, command.errorCode());
            statement.setString(12, command.errorMessage());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to create speech transcription");
        }
        return key.longValue();
    }
}
