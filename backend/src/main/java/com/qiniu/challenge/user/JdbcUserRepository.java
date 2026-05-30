package com.qiniu.challenge.user;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<User> userRowMapper = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("display_name"),
            rs.getString("password_hash"),
            rs.getString("avatar_url"),
            toStatus(rs.getString("status")),
            toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("updated_at")),
            toLocalDateTime(rs.getTimestamp("deleted_at")));

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE username = ? AND deleted_at IS NULL",
                Integer.class,
                username);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE email = ? AND deleted_at IS NULL",
                Integer.class,
                email);
        return count != null && count > 0;
    }

    @Override
    public User save(CreateUserCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO users (username, email, display_name, password_hash, status)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, command.username());
            statement.setString(2, command.email());
            statement.setString(3, command.displayName());
            statement.setString(4, command.passwordHash());
            statement.setString(5, UserStatus.ACTIVE.value());
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to read generated user id");
        }
        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("Created user cannot be found"));
    }

    @Override
    public Optional<User> findById(long id) {
        return jdbcTemplate.query(
                "SELECT * FROM users WHERE id = ? AND deleted_at IS NULL",
                userRowMapper,
                id).stream().findFirst();
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String account) {
        return jdbcTemplate.query(
                """
                        SELECT * FROM users
                        WHERE deleted_at IS NULL
                          AND status = 'active'
                          AND (username = ? OR email = ?)
                        """,
                userRowMapper,
                account,
                account).stream().findFirst();
    }

    private UserStatus toStatus(String status) {
        if (status == null) {
            return UserStatus.DISABLED;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "active" -> UserStatus.ACTIVE;
            case "disabled" -> UserStatus.DISABLED;
            default -> UserStatus.DISABLED;
        };
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
