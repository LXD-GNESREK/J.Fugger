package com.example.ledgerly.security.bruteforce;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

// Reiner Unit-Test ohne Spring: die Zeit wird über eine verstellbare Clock simuliert
class LoginAttemptServiceTest
{
    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp()
    {
        clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));
        service = new LoginAttemptService(clock, 5, Duration.ofMinutes(1), Duration.ofMinutes(15));
    }

    @Test
    void fourFailures_doNotLock()
    {
        fail("alice", 4);
        assertThat(service.remainingLock("alice")).isEmpty();
    }

    @Test
    void fifthFailure_locksForBaseDuration()
    {
        fail("alice", 5);
        assertThat(service.remainingLock("alice")).contains(Duration.ofMinutes(1));
    }

    @Test
    void lockExpires_afterDuration()
    {
        fail("alice", 5);
        clock.advance(Duration.ofMinutes(1));
        assertThat(service.remainingLock("alice")).isEmpty();
    }

    @Test
    void furtherFailures_doubleTheLock_upToMax()
    {
        assertThat(service.lockDuration(5)).isEqualTo(Duration.ofMinutes(1));
        assertThat(service.lockDuration(6)).isEqualTo(Duration.ofMinutes(2));
        assertThat(service.lockDuration(7)).isEqualTo(Duration.ofMinutes(4));
        assertThat(service.lockDuration(8)).isEqualTo(Duration.ofMinutes(8));
        assertThat(service.lockDuration(9)).isEqualTo(Duration.ofMinutes(15));
        assertThat(service.lockDuration(500)).isEqualTo(Duration.ofMinutes(15));   // kein Overflow
    }

    @Test
    void failureAfterLockExpired_locksLonger()
    {
        fail("alice", 5);
        clock.advance(Duration.ofMinutes(1));
        fail("alice", 1);   // 6. Fehlversuch
        assertThat(service.remainingLock("alice")).contains(Duration.ofMinutes(2));
    }

    @Test
    void success_resetsCounter()
    {
        fail("alice", 4);
        service.loginSucceeded("alice");
        fail("alice", 4);
        assertThat(service.remainingLock("alice")).isEmpty();
    }

    @Test
    void usernameIsNormalized()
    {
        fail("Alice", 2);
        fail(" alice ", 2);
        fail("ALICE", 1);
        assertThat(service.remainingLock("alice")).isPresent();
    }

    @Test
    void accountsAreIndependent()
    {
        fail("alice", 5);
        assertThat(service.remainingLock("bob")).isEmpty();
    }

    private void fail(String username, int times)
    {
        for (int i = 0; i < times; i++)
        {
            service.loginFailed(username);
        }
    }

    private static final class MutableClock extends Clock
    {
        private Instant now;

        MutableClock(Instant start) { this.now = start; }

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}