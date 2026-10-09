package com.example.ledgerly.api.controller;

import com.example.ledgerly.api.dto.in.LoginRequest;
import com.example.ledgerly.api.dto.out.LoginResponse;
import com.example.ledgerly.application.service.RefreshTokenService;
import com.example.ledgerly.security.AppUserDetailsService;
import com.example.ledgerly.security.AuthCookieService;
import com.example.ledgerly.security.JwtService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SuppressFBWarnings(
        value = "SPRING_ENDPOINT",
        justification = "Login/Refresh/Logout sind bewusst öffentlich erreichbar; das Pattern ist nur ein Hinweis auf jeden Spring-Controller.")
@RestController
@RequestMapping("/api/auth")
public class AuthController
{
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String GENERIC_ERROR = "Benutzername oder Passwort ist falsch.";

    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService cookies;

    public AuthController(AuthenticationManager authenticationManager,
                          AppUserDetailsService userDetailsService,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          AuthCookieService cookies)
    {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request)
    {
        try
        {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

            UserDetails user = (UserDetails) auth.getPrincipal();
            String refreshToken = refreshTokenService.issueNewFamily(user.getUsername());
            return respondWithTokens(user, refreshToken);
        }
        catch (AuthenticationException ex)
        {
            // Gleiche Antwort für unbekannten User, falsches Passwort und gesperrten Account
            log.warn("Fehlgeschlagener Login-Versuch");   // bewusst ohne User-Eingabe (Log Injection)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", GENERIC_ERROR));
        }
    }

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

    private ResponseEntity<LoginResponse> respondWithTokens(UserDetails user, String refreshToken)
    {
        String role = user.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(jwtService.generateAccessToken(user)).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(refreshToken).toString())
                .body(new LoginResponse(user.getUsername(), role));
    }
}