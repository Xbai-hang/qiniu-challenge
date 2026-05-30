package com.qiniu.challenge.organization;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<OrganizationRecord> organizationRowMapper = (rs, rowNum) -> new OrganizationRecord(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("invite_code"),
            rs.getLong("created_by"),
            rs.getString("status"));

    private final RowMapper<OrganizationSummaryResponse> summaryRowMapper = (rs, rowNum) ->
            new OrganizationSummaryResponse(
                    rs.getLong("organization_id"),
                    rs.getString("name"),
                    rs.getString("role"),
                    rs.getLong("space_id"));

    private final RowMapper<OrganizationMemberResponse> memberRowMapper = (rs, rowNum) ->
            new OrganizationMemberResponse(
                    rs.getLong("user_id"),
                    rs.getString("display_name"),
                    rs.getString("nickname"),
                    rs.getString("title"),
                    rs.getString("role"),
                    rs.getString("status"));

    public JdbcOrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long createOrganization(String name, String inviteCode, long createdBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO organizations (name, invite_code, created_by, status)
                    VALUES (?, ?, ?, 'active')
                    """, new String[]{"id"});
            statement.setString(1, name);
            statement.setString(2, inviteCode);
            statement.setLong(3, createdBy);
            return statement;
        }, keyHolder);
        return requireGeneratedId(keyHolder, "organization");
    }

    @Override
    public long createOrganizationSpace(long organizationId, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO calendar_spaces (type, name, organization_id)
                    VALUES ('organization', ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, name);
            statement.setLong(2, organizationId);
            return statement;
        }, keyHolder);
        return requireGeneratedId(keyHolder, "calendar space");
    }

    @Override
    public void addMember(long organizationId, long userId, OrganizationRole role) {
        jdbcTemplate.update("""
                INSERT INTO organization_members (organization_id, user_id, role, status)
                VALUES (?, ?, ?, 'active')
                """, organizationId, userId, role.value());
    }

    @Override
    public boolean reactivateMember(long organizationId, long userId, OrganizationRole role) {
        int updated = jdbcTemplate.update("""
                UPDATE organization_members
                SET role = ?,
                    status = 'active',
                    joined_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE organization_id = ?
                  AND user_id = ?
                  AND status <> 'active'
                """, role.value(), organizationId, userId);
        return updated > 0;
    }

    @Override
    public boolean hasAnyMemberRecord(long organizationId, long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM organization_members
                WHERE organization_id = ?
                  AND user_id = ?
                """, Integer.class, organizationId, userId);
        return count != null && count > 0;
    }

    @Override
    public Optional<OrganizationRecord> findActiveOrganization(long organizationId) {
        return jdbcTemplate.query("""
                SELECT id, name, invite_code, created_by, status
                FROM organizations
                WHERE id = ?
                  AND status = 'active'
                  AND deleted_at IS NULL
                """, organizationRowMapper, organizationId).stream().findFirst();
    }

    @Override
    public Optional<OrganizationRecord> findActiveOrganizationByInviteCode(String inviteCode) {
        return jdbcTemplate.query("""
                SELECT id, name, invite_code, created_by, status
                FROM organizations
                WHERE invite_code = ?
                  AND status = 'active'
                  AND deleted_at IS NULL
                """, organizationRowMapper, inviteCode).stream().findFirst();
    }

    @Override
    public Optional<OrganizationRole> findActiveMemberRole(long organizationId, long userId) {
        return jdbcTemplate.query("""
                SELECT role
                FROM organization_members
                WHERE organization_id = ?
                  AND user_id = ?
                  AND status = 'active'
                """, (rs, rowNum) -> OrganizationRole.fromValue(rs.getString("role")), organizationId, userId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Long> findOrganizationSpaceId(long organizationId) {
        return jdbcTemplate.query("""
                SELECT id
                FROM calendar_spaces
                WHERE type = 'organization'
                  AND organization_id = ?
                  AND deleted_at IS NULL
                """, (rs, rowNum) -> rs.getLong("id"), organizationId).stream().findFirst();
    }

    @Override
    public List<OrganizationSummaryResponse> findOrganizationsForUser(long userId) {
        return jdbcTemplate.query("""
                SELECT
                  o.id AS organization_id,
                  o.name,
                  m.role,
                  s.id AS space_id
                FROM organization_members m
                JOIN organizations o ON o.id = m.organization_id
                JOIN calendar_spaces s ON s.organization_id = o.id AND s.type = 'organization'
                WHERE m.user_id = ?
                  AND m.status = 'active'
                  AND o.status = 'active'
                  AND o.deleted_at IS NULL
                  AND s.deleted_at IS NULL
                ORDER BY o.id
                """, summaryRowMapper, userId);
    }

    @Override
    public List<OrganizationMemberResponse> findActiveMembers(long organizationId) {
        return jdbcTemplate.query("""
                SELECT
                  m.user_id,
                  u.display_name,
                  COALESCE(m.nickname, u.display_name) AS nickname,
                  m.title,
                  m.role,
                  m.status
                FROM organization_members m
                JOIN users u ON u.id = m.user_id
                WHERE m.organization_id = ?
                  AND m.status = 'active'
                  AND u.deleted_at IS NULL
                ORDER BY
                  CASE m.role
                    WHEN 'owner' THEN 0
                    WHEN 'admin' THEN 1
                    ELSE 2
                  END,
                  m.joined_at,
                  m.user_id
                """, memberRowMapper, organizationId);
    }

    @Override
    public boolean updateInviteCode(long organizationId, String inviteCode) {
        int updated = jdbcTemplate.update("""
                UPDATE organizations
                SET invite_code = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'active'
                  AND deleted_at IS NULL
                """, inviteCode, organizationId);
        return updated > 0;
    }

    @Override
    public boolean updateMemberRole(long organizationId, long userId, OrganizationRole role) {
        int updated = jdbcTemplate.update("""
                UPDATE organization_members
                SET role = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE organization_id = ?
                  AND user_id = ?
                  AND status = 'active'
                """, role.value(), organizationId, userId);
        return updated > 0;
    }

    @Override
    public boolean removeMember(long organizationId, long userId) {
        int updated = jdbcTemplate.update("""
                UPDATE organization_members
                SET status = 'removed',
                    updated_at = CURRENT_TIMESTAMP
                WHERE organization_id = ?
                  AND user_id = ?
                  AND status = 'active'
                """, organizationId, userId);
        return updated > 0;
    }

    private long requireGeneratedId(KeyHolder keyHolder, String entityName) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to read generated " + entityName + " id");
        }
        return key.longValue();
    }
}
