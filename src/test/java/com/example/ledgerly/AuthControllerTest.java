package com.example.ledgerly;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4: AutoConfigureMockMvc liegt evtl. in
// org.springframework.boot.webmvc.test.autoconfigure -> von IntelliJ importieren lassen
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Bewusst KEIN @Transactional an der Klasse: Die Token-Revokation bei Wiederverwendung
// muss wirklich committed werden, sonst testen wir nichts Reales.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest
{
    private static final String USERNAME = "test.user";       // an euren Test-User anpassen
    private static final String PASSWORD = "Test-Passwort-1!"; // an euren Test-User anpassen

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp()
    {
        // die Tests laufen ohne @Transactional, die Daten bleiben sonst bestehen
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from users where username = ?", USERNAME);

        jdbc.update("""
            insert into users (created_at, enabled, first_name, last_name, password_hash, role, username)
            values (CURRENT_TIMESTAMP, true, 'Test', 'User', ?, 'EMPLOYEE', ?)
            """,
                passwordEncoder.encode(PASSWORD), USERNAME);
        jdbc.update("delete from users where username = ?", "gesperrt.user");
        jdbc.update("""
        insert into users (created_at, enabled, first_name, last_name, password_hash, role, username)
        values (CURRENT_TIMESTAMP, false, 'Gesperrt', 'User', ?, 'EMPLOYEE', ?)
        """,
                passwordEncoder.encode(PASSWORD), "gesperrt.user");
    }

    // Login

    @Test
    void login_success_setsSecureHttpOnlyCookies_andNoTokenInBody() throws Exception
    {
        MvcResult result = login(USERNAME, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        String access = findCookieHeader(setCookies, "access_token");
        assertThat(access).contains("HttpOnly").contains("Secure").contains("SameSite=Strict").contains("Path=/");

        String refresh = findCookieHeader(setCookies, "refresh_token");
        assertThat(refresh).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/auth");
    }

    @Test
    void login_wrongPassword_returns401_withGenericMessage() throws Exception
    {
        login(USERNAME, "falsches-passwort")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Benutzername oder Passwort ist falsch."));
    }

    @Test
    void login_unknownUser_returnsSameResponseAsWrongPassword() throws Exception
    {
        // Gleiche Antwort wie bei falschem Passwort -> keine User Enumeration
        login("gibt.es.nicht", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Benutzername oder Passwort ist falsch."));
    }

    // Geschützte Endpoints

    @Test
    void protectedEndpoint_withoutCookie_returns401() throws Exception
    {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidAccessCookie_isNotRejected() throws Exception
    {
        MvcResult loginResult = login(USERNAME, PASSWORD).andReturn();
        Cookie access = loginResult.getResponse().getCookie("access_token");

        MvcResult result = mockMvc.perform(get("/api/expenses").cookie(access)).andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void protectedEndpoint_withTamperedAccessCookie_returns401() throws Exception
    {
        MvcResult loginResult = login(USERNAME, PASSWORD).andReturn();
        String jwt = loginResult.getResponse().getCookie("access_token").getValue();
        // letztes Zeichen der Signatur verändern
        String tampered = jwt.substring(0, jwt.length() - 1) + (jwt.endsWith("A") ? "B" : "A");

        mockMvc.perform(get("/api/expenses").cookie(new Cookie("access_token", tampered)))
                .andExpect(status().isUnauthorized());
    }

    // CSRF

    @Test
    void refresh_withoutCsrfToken_returns403() throws Exception
    {
        MvcResult loginResult = login(USERNAME, PASSWORD).andReturn();
        Cookie refresh = loginResult.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/auth/refresh").cookie(refresh))   // kein csrf()
                .andExpect(status().isForbidden());
    }

    @Test
    void logout_withoutCsrfToken_returns403() throws Exception
    {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
    }

    // Refresh Token Rotation

    @Test
    void refresh_withoutCookie_returns401() throws Exception
    {
        mockMvc.perform(post("/api/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withValidToken_rotatesRefreshToken() throws Exception
    {
        Cookie oldRefresh = login(USERNAME, PASSWORD).andReturn().getResponse().getCookie("refresh_token");

        MvcResult result = mockMvc.perform(post("/api/auth/refresh").cookie(oldRefresh).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andReturn();

        Cookie newRefresh = result.getResponse().getCookie("refresh_token");
        assertThat(newRefresh).isNotNull();
        assertThat(newRefresh.getValue()).isNotEqualTo(oldRefresh.getValue());
        assertThat(result.getResponse().getCookie("access_token")).isNotNull();
    }

    @Test
    void refresh_reuseOfOldToken_returns401_andRevokesWholeFamily() throws Exception
    {
        Cookie tokenA = login(USERNAME, PASSWORD).andReturn().getResponse().getCookie("refresh_token");

        // 1. Legitime Rotation: A -> B
        Cookie tokenB = mockMvc.perform(post("/api/auth/refresh").cookie(tokenA).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("refresh_token");

        // 2. Angreifer benutzt das alte Token A erneut -> abgelehnt
        mockMvc.perform(post("/api/auth/refresh").cookie(tokenA).with(csrf()))
                .andExpect(status().isUnauthorized());

        // 3. Auch das aktuelle Token B ist jetzt ungültig (ganze Familie widerrufen)
        mockMvc.perform(post("/api/auth/refresh").cookie(tokenB).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withGarbageToken_returns401() throws Exception
    {
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "gibt-es-nicht")).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    //  Logout

    @Test
    void logout_clearsCookies_andInvalidatesRefreshToken() throws Exception
    {
        Cookie refresh = login(USERNAME, PASSWORD).andReturn().getResponse().getCookie("refresh_token");

        MvcResult result = mockMvc.perform(post("/api/auth/logout").cookie(refresh).with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(findCookieHeader(setCookies, "access_token")).contains("Max-Age=0");
        assertThat(findCookieHeader(setCookies, "refresh_token")).contains("Max-Age=0");

        // Das alte Refresh-Token darf nach dem Logout nicht mehr funktionieren
        mockMvc.perform(post("/api/auth/refresh").cookie(refresh).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // Helper

    private org.springframework.test.web.servlet.ResultActions login(String username, String password)
            throws Exception
    {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);

        // Login ist von CSRF ausgenommen -> kein csrf() nötig
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String findCookieHeader(List<String> setCookieHeaders, String name)
    {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie für " + name + " fehlt"));
    }
}