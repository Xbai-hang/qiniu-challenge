package com.qiniu.challenge.space;

import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCalendarSpaceRepository implements CalendarSpaceRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<CalendarSpaceResponse> responseRowMapper = (rs, rowNum) -> new CalendarSpaceResponse(
            rs.getLong("id"),
            rs.getString("type"),
            rs.getString("name"),
            getNullableLong(rs, "organization_id"),
            rs.getString("role"));

    public JdbcCalendarSpaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CalendarSpaceResponse createPersonalSpace(long ownerUserId, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO calendar_spaces (type, name, owner_user_id)
                    VALUES ('personal', ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, name);
            statement.setLong(2, ownerUserId);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to read generated calendar space id");
        }

        return findPersonalSpaceById(key.longValue());
    }

    @Override
    public void ensurePersonalSpaceExists(long ownerUserId) {
        jdbcTemplate.update("""
                INSERT INTO calendar_spaces (type, name, owner_user_id)
                SELECT 'personal', CONCAT(u.display_name, ' 的个人日历'), u.id
                FROM users u
                WHERE u.id = ?
                  AND u.deleted_at IS NULL
                  AND NOT EXISTS (
                    SELECT 1
                    FROM calendar_spaces s
                    WHERE s.type = 'personal'
                      AND s.owner_user_id = u.id
                      AND s.organization_id IS NULL
                      AND s.deleted_at IS NULL
                  )
                """, ownerUserId);
    }

    @Override
    public List<CalendarSpaceResponse> findAccessibleSpaces(long userId) {
        return jdbcTemplate.query("""
                SELECT id, type, name, organization_id, role
                FROM (
                  SELECT
                    s.id,
                    s.type,
                    s.name,
                    s.organization_id,
                    'owner' AS role,
                    0 AS sort_order
                  FROM calendar_spaces s
                  WHERE s.type = 'personal'
                    AND s.owner_user_id = ?
                    AND s.deleted_at IS NULL

                  UNION ALL

                  SELECT
                    s.id,
                    s.type,
                    s.name,
                    s.organization_id,
                    m.role,
                    1 AS sort_order
                  FROM calendar_spaces s
                  JOIN organization_members m ON m.organization_id = s.organization_id
                  JOIN organizations o ON o.id = s.organization_id
                  WHERE s.type = 'organization'
                    AND m.user_id = ?
                    AND m.status = 'active'
                    AND o.status = 'active'
                    AND s.deleted_at IS NULL
                    AND o.deleted_at IS NULL
                ) accessible_spaces
                ORDER BY sort_order, id
                """, responseRowMapper, userId, userId);
    }

    private CalendarSpaceResponse findPersonalSpaceById(long id) {
        return jdbcTemplate.query("""
                SELECT id, type, name, organization_id, 'owner' AS role
                FROM calendar_spaces
                WHERE id = ? AND type = 'personal' AND deleted_at IS NULL
                """, responseRowMapper, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created calendar space cannot be found"));
    }

    private Long getNullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
