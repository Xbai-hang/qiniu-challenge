package com.qiniu.challenge.organization;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createOrganizationCreatesOwnerMemberAndOrganizationSpace() throws Exception {
        RegisteredUser owner = register("org_owner_create", "org-owner-create@example.com", "Owner Create");

        MvcResult result = createOrganization(owner, "Alpha 团队")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.organizationId", notNullValue()))
                .andExpect(jsonPath("$.data.spaceId", notNullValue()))
                .andExpect(jsonPath("$.data.name").value("Alpha 团队"))
                .andExpect(jsonPath("$.data.role").value("owner"))
                .andExpect(jsonPath("$.data.inviteCode", notNullValue()))
                .andReturn();

        JsonNode data = data(result);
        long organizationId = data.path("organizationId").asLong();
        long spaceId = data.path("spaceId").asLong();

        Integer ownerCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM organization_members
                WHERE organization_id = ?
                  AND user_id = ?
                  AND role = 'owner'
                  AND status = 'active'
                """, Integer.class, organizationId, owner.id());
        Integer spaceCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM calendar_spaces
                WHERE id = ?
                  AND organization_id = ?
                  AND type = 'organization'
                  AND name = ?
                """, Integer.class, spaceId, organizationId, "Alpha 团队");

        org.hamcrest.MatcherAssert.assertThat(ownerCount, org.hamcrest.Matchers.is(1));
        org.hamcrest.MatcherAssert.assertThat(spaceCount, org.hamcrest.Matchers.is(1));
    }

    @Test
    void myOrganizationsReturnsJoinedOrganizationsAndRoles() throws Exception {
        RegisteredUser owner = register("org_owner_list", "org-owner-list@example.com", "Owner List");
        JsonNode created = data(createOrganization(owner, "Beta 团队").andReturn());

        mockMvc.perform(get("/api/organizations")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].organizationId").value(created.path("organizationId").asLong()))
                .andExpect(jsonPath("$.data[0].name").value("Beta 团队"))
                .andExpect(jsonPath("$.data[0].role").value("owner"))
                .andExpect(jsonPath("$.data[0].spaceId").value(created.path("spaceId").asLong()));
    }

    @Test
    void ownerCanRefreshInviteCodeButMemberCannot() throws Exception {
        RegisteredUser owner = register("org_owner_invite", "org-owner-invite@example.com", "Owner Invite");
        RegisteredUser member = register("org_member_invite", "org-member-invite@example.com", "Member Invite");
        JsonNode created = data(createOrganization(owner, "Gamma 团队").andReturn());
        joinOrganization(member, created.path("inviteCode").asText()).andExpect(status().isOk());

        mockMvc.perform(post("/api/organizations/{organizationId}/invite-code/refresh",
                        created.path("organizationId").asLong())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inviteCode", not(created.path("inviteCode").asText())));

        mockMvc.perform(post("/api/organizations/{organizationId}/invite-code/refresh",
                        created.path("organizationId").asLong())
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void userCanJoinByInviteCodeButCannotJoinTwice() throws Exception {
        RegisteredUser owner = register("org_owner_join", "org-owner-join@example.com", "Owner Join");
        RegisteredUser member = register("org_member_join", "org-member-join@example.com", "Member Join");
        JsonNode created = data(createOrganization(owner, "Delta 团队").andReturn());

        joinOrganization(member, created.path("inviteCode").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.organizationId").value(created.path("organizationId").asLong()))
                .andExpect(jsonPath("$.data.spaceId").value(created.path("spaceId").asLong()))
                .andExpect(jsonPath("$.data.role").value("member"));

        joinOrganization(member, created.path("inviteCode").asText())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void organizationMembersCanViewMemberListOrderedByRole() throws Exception {
        RegisteredUser owner = register("org_owner_members", "org-owner-members@example.com", "Owner Members");
        RegisteredUser admin = register("org_admin_members", "org-admin-members@example.com", "Admin Members");
        RegisteredUser member = register("org_member_members", "org-member-members@example.com", "Member Members");
        JsonNode created = data(createOrganization(owner, "Epsilon 团队").andReturn());
        long organizationId = created.path("organizationId").asLong();
        joinOrganization(admin, created.path("inviteCode").asText()).andExpect(status().isOk());
        joinOrganization(member, created.path("inviteCode").asText()).andExpect(status().isOk());
        updateRole(owner, organizationId, admin.id(), "admin").andExpect(status().isOk());

        mockMvc.perform(get("/api/organizations/{organizationId}/members", organizationId)
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(owner.id()))
                .andExpect(jsonPath("$.data[0].displayName").value("Owner Members"))
                .andExpect(jsonPath("$.data[0].nickname").value("Owner Members"))
                .andExpect(jsonPath("$.data[0].role").value("owner"))
                .andExpect(jsonPath("$.data[0].status").value("active"))
                .andExpect(jsonPath("$.data[1].userId").value(admin.id()))
                .andExpect(jsonPath("$.data[1].role").value("admin"))
                .andExpect(jsonPath("$.data[2].userId").value(member.id()))
                .andExpect(jsonPath("$.data[2].role").value("member"));
    }

    @Test
    void onlyOwnerCanUpdateMemberRoleAndOwnerRoleCannotBeChanged() throws Exception {
        RegisteredUser owner = register("org_owner_role", "org-owner-role@example.com", "Owner Role");
        RegisteredUser admin = register("org_admin_role", "org-admin-role@example.com", "Admin Role");
        RegisteredUser member = register("org_member_role", "org-member-role@example.com", "Member Role");
        JsonNode created = data(createOrganization(owner, "Zeta 团队").andReturn());
        long organizationId = created.path("organizationId").asLong();
        joinOrganization(admin, created.path("inviteCode").asText()).andExpect(status().isOk());
        joinOrganization(member, created.path("inviteCode").asText()).andExpect(status().isOk());

        updateRole(owner, organizationId, admin.id(), "admin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        updateRole(admin, organizationId, member.id(), "admin")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        updateRole(owner, organizationId, owner.id(), "member")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void ownerOrAdminCanRemoveMemberButCannotRemoveOwnerOrAdmin() throws Exception {
        RegisteredUser owner = register("org_owner_remove", "org-owner-remove@example.com", "Owner Remove");
        RegisteredUser admin = register("org_admin_remove", "org-admin-remove@example.com", "Admin Remove");
        RegisteredUser member = register("org_member_remove", "org-member-remove@example.com", "Member Remove");
        JsonNode created = data(createOrganization(owner, "Eta 团队").andReturn());
        long organizationId = created.path("organizationId").asLong();
        joinOrganization(admin, created.path("inviteCode").asText()).andExpect(status().isOk());
        joinOrganization(member, created.path("inviteCode").asText()).andExpect(status().isOk());
        updateRole(owner, organizationId, admin.id(), "admin").andExpect(status().isOk());

        mockMvc.perform(delete("/api/organizations/{organizationId}/members/{userId}", organizationId, owner.id())
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/organizations/{organizationId}/members/{userId}", organizationId, admin.id())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/organizations/{organizationId}/members/{userId}", organizationId, member.id())
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/organizations/{organizationId}/members", organizationId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void organizationEndpointsRequireToken() throws Exception {
        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private org.springframework.test.web.servlet.ResultActions createOrganization(
            RegisteredUser owner,
            String name) throws Exception {
        return mockMvc.perform(post("/api/organizations")
                .header("Authorization", "Bearer " + owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "%s"
                        }
                        """.formatted(name)));
    }

    private org.springframework.test.web.servlet.ResultActions joinOrganization(
            RegisteredUser user,
            String inviteCode) throws Exception {
        return mockMvc.perform(post("/api/organizations/join")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "inviteCode": "%s"
                        }
                        """.formatted(inviteCode)));
    }

    private org.springframework.test.web.servlet.ResultActions updateRole(
            RegisteredUser operator,
            long organizationId,
            long userId,
            String role) throws Exception {
        return mockMvc.perform(patch("/api/organizations/{organizationId}/members/{userId}/role", organizationId, userId)
                .header("Authorization", "Bearer " + operator.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "role": "%s"
                        }
                        """.formatted(role)));
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
        return new RegisteredUser(
                data.path("user").path("id").asLong(),
                data.path("accessToken").asText());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private record RegisteredUser(long id, String token) {
    }
}
