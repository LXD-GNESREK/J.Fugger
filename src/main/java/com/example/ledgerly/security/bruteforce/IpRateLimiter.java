package com.example.ledgerly.security.bruteforce;

import org.springframework.stereotype.Component;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

/**
 * IP-basiertes Rate Limiting (Token Bucket, Bucket4j) für die Auth-Endpoints.
 * Bremst Credential Stuffing von einer IP aus, auch wenn dabei viele verschiedene
 * Usernamen probiert werden (das fängt das account-basierte Lockout allein nicht ab).
 */
@Component
public class IpRateLimiter {
    private final Cache<String, Bucket> buckets;
    private final long perMinute;
    private final long perHour;

    public IpRateLimiter(@Value("${app.security.rate-limit.per-minute:20}") long perMinute,
                         @Value("${app.security.rate-limit.per-hour:200}") long perHour)
    {
        this.perMinute = perMinute;
        this.perHour = perHour;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(Duration.ofHours(1))   // danach wäre der Bucket ohnehin wieder voll
                .build();
    }

    /** @return 0 wenn der Request erlaubt ist, sonst Wartezeit in Sekunden (für Retry-After). */
    public long tryConsume(String clientIp)
    {
        Bucket bucket = buckets.get(clientIp, ip -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed())
        {
            return 0;
        }
        long seconds = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1;   // aufrunden
        return Math.max(seconds, 1);
    }

    private Bucket newBucket()
    {
        return Bucket.builder()
                // kurzfristige Bursts begrenzen
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                // langsames "low and slow" Durchprobieren begrenzen
                .addLimit(Bandwidth.builder().capacity(perHour).refillGreedy(perHour, Duration.ofHours(1)).build())
                .build();
    }
}
