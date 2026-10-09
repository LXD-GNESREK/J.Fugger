package com.example.ledgerly.api.controller;

import com.example.ledgerly.api.dto.in.LoginRequest;
import com.example.ledgerly.api.dto.out.LoginResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SuppressFBWarnings(
        value = "SPRING_ENDPOINT",
        justification = "Der Login-Endpoint ist bewusst öffentlich erreichbar; das Pattern ist nur ein Hinweis auf jeden Spring-Controller.")
@RestController
@RequestMapping("/api/auth")
public class AuthController
{
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String GENERIC_ERROR = "Benutzername oder Passwort ist falsch.";

    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager)
    {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request)
    {
        try
        {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

            String role = auth.getAuthorities().iterator().next().getAuthority();
            return ResponseEntity.ok(new LoginResponse(auth.getName(), role));
        }
        catch (AuthenticationException ex)
        {
            // Gleiche Antwort für unbekannten User, falsches Passwort und gesperrten Account
            log.warn("Fehlgeschlagener Login-Versuch");   // bewusst ohne User-Eingabe (Log Injection)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", GENERIC_ERROR));
        }
    }
}