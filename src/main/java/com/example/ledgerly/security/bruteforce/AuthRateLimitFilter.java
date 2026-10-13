package com.example.ledgerly.security.bruteforce;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Bewusst KEIN @Component: wird in SecurityConfig in die Filterkette eingehängt
// (sonst registriert Spring Boot ihn zusätzlich als normalen Servlet-Filter).
public class AuthRateLimitFilter extends OncePerRequestFilter
{
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    // Password-Reset gibt es noch nicht, ist aber schon mitgeschützt, sobald er unter diesem Pfad liegt
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/password-reset");

    private static final String BODY =
            "{\"message\":\"Zu viele Anfragen. Bitte später erneut versuchen.\"}";

    private final IpRateLimiter rateLimiter;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring-verwaltete Bean (Dependency Injection).")
    public AuthRateLimitFilter(IpRateLimiter rateLimiter)
    {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PROTECTED_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException
    {
        // getRemoteAddr() statt X-Forwarded-For: den Header kann jeder Client selbst setzen
        // und damit pro Request eine "neue IP" vortäuschen.
        long retryAfter = rateLimiter.tryConsume(request.getRemoteAddr());
        if (retryAfter > 0)
        {
            log.warn("IP-Rate-Limit für Auth-Endpoint überschritten");   // bewusst ohne IP/User-Eingabe
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(BODY);
            return;
        }
        chain.doFilter(request, response);
    }
}