package com.example.ledgerly.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_PATH = "/api/auth";

    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final boolean secure;

    public AuthCookieService(@Value("${app.jwt.access-token-ttl}") Duration accessTtl,
                             @Value("${app.jwt.refresh-token-ttl}") Duration refreshTtl,
                             @Value("${app.cookie.secure}") boolean secure) {
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.secure = secure;
    }

    public ResponseCookie access(String jwt) {
        return build(ACCESS_COOKIE, jwt, "/", accessTtl);
    }

    public ResponseCookie refresh(String token) {
        return build(REFRESH_COOKIE, token, REFRESH_PATH, refreshTtl);
    }

    public ResponseCookie clearAccess() {
        return build(ACCESS_COOKIE, "", "/", Duration.ZERO);
    }

    public ResponseCookie clearRefresh() {
        return build(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}