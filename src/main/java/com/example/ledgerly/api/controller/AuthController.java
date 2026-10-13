package com.example.ledgerly.api.controller;

import com.example.ledgerly.api.dto.in.LoginRequest;
import com.example.ledgerly.api.dto.out.LoginResponse;
import com.example.ledgerly.application.service.RefreshTokenService;
import com.example.ledgerly.security.AccessRules;
import com.example.ledgerly.security.AppUserDetailsService;
import com.example.ledgerly.security.AuthCookieService;
import com.example.ledgerly.security.JwtService;
import com.example.ledgerly.security.bruteforce.LoginAttemptService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@SuppressFBWarnings(
        value = "SPRING_ENDPOINT",
        justification = "Login/Refresh/Logout sind bewusst öffentlich erreichbar; das Pattern ist nur ein Hinweis auf jeden Spring-Controller.")
@RestController
@RequestMapping("/api/auth")
public class AuthController
{
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String GENERIC_ERROR = "Benutzername oder Passwort ist falsch.";
    private static final String LOCKED_ERROR =
            "Zu viele fehlgeschlagene Anmeldeversuche. Bitte später erneut versuchen.";

    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService cookies;
    private final LoginAttemptService loginAttempts;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring-verwaltete Beans (Dependency Injection); es werden keine veränderbaren Daten von außen übernommen.")
    public AuthController(AuthenticationManager authenticationManager,
                          AppUserDetailsService userDetailsService,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          AuthCookieService cookies,
                          LoginAttemptService loginAttempts)
    {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.cookies = cookies;
        this.loginAttempts = loginAttempts;
    }

    @PreAuthorize(AccessRules.PUBLIC)
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request)
    {
        // Gesperrt -> Passwort gar nicht erst prüfen (auch ein RICHTIGES Passwort wird abgelehnt,
        // sonst könnte ein Angreifer während der Sperre weiter raten)
        Optional<Duration> lock = loginAttempts.remainingLock(request.username());
        if (lock.isPresent())
        {
            log.warn("Login abgelehnt: Account temporär gesperrt");
            long retryAfter = Math.max(1, lock.get().toSeconds() + 1);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter))
                    .body(Map.of("message", LOCKED_ERROR));
        }

        try
        {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

            loginAttempts.loginSucceeded(request.username());
            UserDetails user = (UserDetails) auth.getPrincipal();
            String refreshToken = refreshTokenService.issueNewFamily(user.getUsername());
            return respondWithTokens(user, refreshToken);
        }
        catch (AuthenticationException ex)
        {
            // Gleiche Antwort für unbekannten User, falsches Passwort und gesperrten Account
            log.warn("Fehlgeschlagener Login-Versuch");   // bewusst ohne User-Eingabe (Log Injection)
            loginAttempts.loginFailed(request.username());   // zählt auch für unbekannte User (keine Enumeration)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", GENERIC_ERROR));
        }
    }

    @PreAuthorize(AccessRules.PUBLIC)
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE, required = false) String refreshToken)
    {
        try
        {
            if (refreshToken == null)
            {
                throw new BadCredentialsException("Kein Refresh-Token");
            }

            RefreshTokenService.Issued issued = refreshTokenService.rotate(refreshToken);
            UserDetails user = userDetailsService.loadUserByUsername(issued.username());

            if (!user.isEnabled())   // in der Zwischenzeit vom Admin gesperrt
            {
                refreshTokenService.revoke(issued.rawToken());
                throw new BadCredentialsException("Account gesperrt");
            }

            return respondWithTokens(user, issued.rawToken());
        }
        catch (AuthenticationException ex)
        {
            log.warn("Refresh abgelehnt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString())
                    .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                    .body(Map.of("message", "Sitzung abgelaufen."));
        }
    }

    @PreAuthorize(AccessRules.PUBLIC)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE, required = false) String refreshToken)
    {
        refreshTokenService.revoke(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .build();
    }

    /**
     * Wer bin ich? Das Angular-Frontend kann das HttpOnly-Cookie nicht lesen und fragt hier
     * nach Username + Rolle, z. B. nach einem Reload der Seite. Nur für die UI (Route Guards),
     * die echte Prüfung passiert immer im Backend.
     */
    @PreAuthorize(AccessRules.AUTHENTICATED)
    @GetMapping("/me")
    public LoginResponse me(@AuthenticationPrincipal UserDetails user)
    {
        String role = user.getAuthorities().iterator().next().getAuthority();
        return new LoginResponse(user.getUsername(), role);
    }

    private ResponseEntity<LoginResponse> respondWithTokens(UserDetails user, String refreshToken)
    {
        String role = user.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(jwtService.generateAccessToken(user)).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(refreshToken).toString())
                .body(new LoginResponse(user.getUsername(), role));
    }
}