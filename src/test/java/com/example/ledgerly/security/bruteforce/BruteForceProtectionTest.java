package com.example.ledgerly.security.bruteforce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// IP-Limit hier hoch (aus application-test.properties) -> getestet wird nur das Account-Lockout
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BruteForceProtectionTest
{
    private static final String PASSWORD = "Test-Passwort-1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void fiveFailures_thenEvenCorrectPasswordIsRejectedWith429() throws Exception
    {
        String username = createUser();   // eigener User pro Test, Zähler leben im Spring-Context weiter

        for (int i = 0; i < 4; i++)
        {
            login(username, "falsch").andExpect(status().isUnauthorized());
        }
        login(username, "falsch").andExpect(status().isUnauthorized());   // 5. Versuch -> Sperre beginnt

        login(username, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void successBeforeLimit_resetsCounter() throws Exception
    {
        String username = createUser();

        for (int i = 0; i < 4; i++)
        {
            login(username, "falsch").andExpect(status().isUnauthorized());
        }
        login(username, PASSWORD).andExpect(status().isOk());
        login(username, "falsch").andExpect(status().isUnauthorized());   // nicht 429
    }

    @Test
    void unknownUser_isLockedTheSameWay_noUserEnumeration() throws Exception
    {
        String unknown = "gibt.es.nicht." + UUID.randomUUID().toString().substring(0, 8);

        for (int i = 0; i < 5; i++)
        {
            login(unknown, "falsch").andExpect(status().isUnauthorized());
        }
        login(unknown, "falsch").andExpect(status().isTooManyRequests());
    }

    private String createUser()
    {
        String username = "bf." + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
            insert into users (created_at, enabled, first_name, last_name, password_hash, role, username)
            values (CURRENT_TIMESTAMP, true, 'Brute', 'Force', ?, 'EMPLOYEE', ?)
            """, passwordEncoder.encode(PASSWORD), username);
        return username;
    }

    private ResultActions login(String username, String password) throws Exception
    {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password)));
    }
}