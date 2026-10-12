package com.example.ledgerly.application.service;

import com.example.ledgerly.domain.entity.RefreshToken;
import com.example.ledgerly.domain.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    public record Issued(String username, String rawToken) { }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration refreshTtl;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.jwt.refresh-token-ttl}") Duration refreshTtl) {
        this.repository = repository;
        this.refreshTtl = refreshTtl;
    }

    /** Beim Login: neue Token-Familie. */
    @Transactional
    public String issueNewFamily(String username) {
        return create(username, UUID.randomUUID());
    }

    /**
     * Rotation: altes Token wird ungültig, neues in derselben Familie ausgestellt.
     * noRollbackFor: die Revokation der Familie bei Wiederverwendung muss
     * auch dann gespeichert werden, wenn wir eine Exception werfen.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public Issued rotate(String rawToken) {
        RefreshToken stored = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Ungültiges Token"));

        if (stored.isRevoked()) {
            // Wiederverwendung eines bereits benutzten Tokens -> Diebstahl-Verdacht
            repository.revokeFamily(stored.getFamilyId());
            throw new BadCredentialsException("Token wiederverwendet");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.revoke();
            throw new BadCredentialsException("Token abgelaufen");
        }

        stored.revoke();
        String next = create(stored.getUsername(), stored.getFamilyId());
        return new Issued(stored.getUsername(), next);
    }

    /** Logout: ganze Familie widerrufen (idempotent, wirft nie). */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId()));
    }

    private String create(String username, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(hash(raw), username, familyId,
                Instant.now().plus(refreshTtl)));
        return raw;
    }

    private static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}