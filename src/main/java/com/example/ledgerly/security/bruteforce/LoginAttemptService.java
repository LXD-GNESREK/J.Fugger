package com.example.ledgerly.security.bruteforce;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Account-basierter Brute-Force-Schutz (Issue 2.3).
 *
 * Ab {@code maxFailures} Fehlversuchen wird der Account temporär gesperrt. Jeder weitere
 * Fehlversuch nach Ablauf einer Sperre verdoppelt die Sperrdauer (exponentielles Backoff),
 * gedeckelt bei {@code maxLock}. Ein erfolgreicher Login setzt den Zähler zurück.
 *
 * Wichtig: Auch NICHT existierende Usernamen werden gezählt und gesperrt. Sonst könnte man
 * an der unterschiedlichen Antwort (401 vs. 429) erkennen, ob ein Account existiert.
 */
@Service
public class LoginAttemptService {
    record AttemptState(int failures, Instant lockedUntil) { }

    private final Cache<String, AttemptState> attempts;
    private final Clock clock;
    private final int maxFailures;
    private final Duration baseLock;
    private final Duration maxLock;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Clock ist eine Spring-Bean (Dependency Injection), wird nur gelesen.")
    public LoginAttemptService(Clock clock,
                               @Value("${app.security.login.max-failures:5}") int maxFailures,
                               @Value("${app.security.login.base-lock:PT1M}") Duration baseLock,
                               @Value("${app.security.login.max-lock:PT15M}") Duration maxLock)
    {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.baseLock = baseLock;
        this.maxLock = maxLock;

        // Begrenzt, damit ein Angreifer mit Millionen zufälliger Usernamen nicht den Heap füllt.
        // Ein Eintrag verfällt 24h nach dem letzten Fehlversuch.
        this.attempts = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofHours(24))
                .build();
    }

    /** Restdauer der Sperre, oder leer wenn der Account gerade nicht gesperrt ist. */
    public Optional<Duration> remainingLock(String username)
    {
        AttemptState state = attempts.getIfPresent(key(username));
        if (state == null || state.lockedUntil() == null)
        {
            return Optional.empty();
        }
        Duration remaining = Duration.between(Instant.now(clock), state.lockedUntil());
        return remaining.isNegative() || remaining.isZero() ? Optional.empty() : Optional.of(remaining);
    }

    public void loginFailed(String username)
    {
        Instant now = Instant.now(clock);
        // compute() ist atomar pro Key -> parallele Requests verlieren keine Fehlversuche
        attempts.asMap().compute(key(username), (k, old) ->
        {
            int failures = (old == null ? 0 : old.failures()) + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockDuration(failures)) : null;
            return new AttemptState(failures, lockedUntil);
        });
    }

    public void loginSucceeded(String username)
    {
        attempts.invalidate(key(username));
    }

    /** 5. Fehlversuch -> base, 6. -> 2*base, 7. -> 4*base ... maximal maxLock. */
    Duration lockDuration(int failures)
    {
        int exponent = Math.min(failures - maxFailures, 20);   // Overflow-Schutz
        Duration lock = baseLock.multipliedBy(1L << exponent);
        return lock.compareTo(maxLock) > 0 ? maxLock : lock;
    }

    // "Admin" und "admin " dürfen nicht als zwei verschiedene Zähler gelten
    private static String key(String username)
    {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}


