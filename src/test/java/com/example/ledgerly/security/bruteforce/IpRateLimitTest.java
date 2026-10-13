package com.example.ledgerly.security.bruteforce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Eigener Spring-Context mit kleinem IP-Limit
@SpringBootTest(properties = "app.security.rate-limit.per-minute=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IpRateLimitTest
{
    @Autowired private MockMvc mockMvc;

    @Test
    void moreThanLimitFromSameIp_returns429() throws Exception
    {
        String ip = "203.0.113.10";
        // jedes Mal ein anderer (unbekannter) User -> simuliert Credential Stuffing,
        // das Account-Lockout greift hier also NICHT, nur das IP-Limit
        for (int i = 0; i < 3; i++)
        {
            loginFrom(ip).andExpect(status().isUnauthorized());
        }
        loginFrom(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void otherIp_isNotAffected() throws Exception
    {
        for (int i = 0; i < 4; i++)
        {
            loginFrom("203.0.113.20");
        }
        loginFrom("203.0.113.21").andExpect(status().isUnauthorized());
    }

    @Test
    void refreshEndpoint_isAlsoLimited() throws Exception
    {
        String ip = "203.0.113.30";
        for (int i = 0; i < 3; i++)
        {
            mockMvc.perform(post("/api/auth/refresh").with(csrf()).with(r -> { r.setRemoteAddr(ip); return r; }))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/refresh").with(csrf()).with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void forwardedForHeader_cannotBypassLimit() throws Exception
    {
        String ip = "203.0.113.40";
        for (int i = 0; i < 3; i++)
        {
            loginFrom(ip);
        }
        mockMvc.perform(post("/api/auth/login")
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .header("X-Forwarded-For", "198.51.100.99")   // gefälschte IP
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void nonAuthEndpoints_areNotLimited() throws Exception
    {
        String ip = "203.0.113.50";
        for (int i = 0; i < 10; i++)
        {
            mockMvc.perform(get("/api/expenses").with(r -> { r.setRemoteAddr(ip); return r; }))
                    .andExpect(status().isUnauthorized());   // 401 (nicht eingeloggt), aber nie 429
        }
    }

    private ResultActions loginFrom(String ip) throws Exception
    {
        String user = "stuffing." + UUID.randomUUID().toString().substring(0, 8);
        return mockMvc.perform(post("/api/auth/login")
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"falsch"}
                        """.formatted(user)));
    }
}