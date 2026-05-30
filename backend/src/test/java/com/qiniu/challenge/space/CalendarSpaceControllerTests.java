package com.qiniu.challenge.space;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class CalendarSpaceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mySpacesRequiresToken() throws Exception {
        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void mySpacesReturnsPersonalSpace() throws Exception {
        RegisteredUser user = register("space_alice", "space-alice@example.com", "Space Alice");

        mockMvc.perform(get("/api/spaces")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].type").value("personal"))
                .andExpect(jsonPath("$.data[0].name").value("Space Alice 的个人日历"))
                .andExpect(jsonPath("$.data[0].organizationId").doesNotExist())
                .andExpect(jsonPath("$.data[0].role").value("owner"));
    }

    @Test
    void mySpacesReturnsAccessibleOrganizationSpaces() throws Exception {
        RegisteredUser user = register("space_bob", "space-bob@example.com", "Space Bob");
        long organizationId = createOrganization(user.id(), "Alpha 团队", "SPACE_BOB_ALPHA");
        createOrganizationMember(organizationId, user.id(), "admin");
        createOrganizationSpace(organizationId, "Alpha 团队");

        mockMvc.perform(get("/api/spaces")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].type").value("personal"))
                .andExpect(jsonPath("$.data[0].role").value("owner"))
                .andExpect(jsonPath("$.data[1].type").value("organization"))
                .andExpect(jsonPath("$.data[1].name").value("Alpha 团队"))
                .andExpect(jsonPath("$.data[1].organizationId").value(organizationId))
                .andExpect(jsonPath("$.data[1].role").value("admin"));
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

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.path("data");
        return new RegisteredUser(
                data.path("user").path("id").asLong(),
                data.path("accessToken").asText());
    }

    private long createOrganization(long createdBy, String name, String inviteCode) {
        jdbcTemplate.update("""
                INSERT INTO organizations (name, invite_code, created_by, status)
                VALUES (?, ?, ?, 'active')
                """, name, inviteCode, createdBy);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM organizations WHERE invite_code = ?",
                Long.class,
                inviteCode);
    }

    private void createOrganizationMember(long organizationId, long userId, String role) {
        jdbcTemplate.update("""
                INSERT INTO organization_members (organization_id, user_id, role, status)
                VALUES (?, ?, ?, 'active')
                """, organizationId, userId, role);
    }

    private void createOrganizationSpace(long organizationId, String name) {
        jdbcTemplate.update("""
                INSERT INTO calendar_spaces (type, name, organization_id)
                VALUES ('organization', ?, ?)
                """, name, organizationId);
    }

    private record RegisteredUser(long id, String token) {
    }
}
