package com.example.ledgerly.security.rbac;

import com.example.ledgerly.security.AccessRules;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MethodSecurityTest.ProbeController.class)
class MethodSecurityTest
{
    private static final String PASSWORD = "Test-Passwort-1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    /** Nur für diesen Test: ein Endpoint MIT und einer OHNE @PreAuthorize. */
    @RestController
    @RequestMapping("/api/test-probe")
    static class ProbeController
    {
        @PreAuthorize(AccessRules.MANAGER)
        @GetMapping("/managers")
        public String managersOnly()
        {
            return "ok";
        }

        @GetMapping("/forgotten")   // absichtlich OHNE @PreAuthorize
        public String forgotten()
        {
            return "darf nie erreicht werden";
        }
    }

    // ---------- Default-Deny ----------

    @Test
    void methodWithoutPreAuthorize_isDenied_evenForFinanceAdmin() throws Exception
    {
        mockMvc.perform(get("/api/test-probe/forgotten").with(user("boss").roles("FINANCE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    // ---------- Rollen ----------

    @Test
    void managerEndpoint_allowsManager() throws Exception
    {
        mockMvc.perform(get("/api/test-probe/managers").with(user("m").roles("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void managerEndpoint_deniesEmployee_with403_not500() throws Exception
    {
        mockMvc.perform(get("/api/test-probe/managers").with(user("e").roles("EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    void managerEndpoint_withoutLogin_returns401() throws Exception
    {
        mockMvc.perform(get("/api/test-probe/managers"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- /api/auth/me (für die Angular Guards) ----------

    @Test
    void me_afterLogin_returnsUsernameAndRole() throws Exception
    {
        String username = "rbac." + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
            insert into users (created_at, enabled, first_name, last_name, password_hash, role, username)
            values (CURRENT_TIMESTAMP, true, 'Finance', 'Admin', ?, 'FINANCE_ADMIN', ?)
            """, passwordEncoder.encode(PASSWORD), username);

        Cookie access = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("access_token");

        mockMvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("ROLE_FINANCE_ADMIN"));
    }

    @Test
    void me_withoutLogin_returns401() throws Exception
    {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}