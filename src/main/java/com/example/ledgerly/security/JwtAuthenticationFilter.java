package com.example.ledgerly.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Bewusst KEIN @Component: wird in SecurityConfig in die Filterkette eingehängt,
// sonst registriert Spring Boot ihn zusätzlich als normalen Servlet-Filter.
public class JwtAuthenticationFilter extends OncePerRequestFilter
{
    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService)
    {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException
    {
        String token = readAccessCookie(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null)
        {
            try
            {
                String username = jwtService.extractUsername(token);
                UserDetails user = userDetailsService.loadUserByUsername(username);

                // isEnabled() fängt auch Accounts ab, die nach der Token-Ausstellung gesperrt wurden
                if (user.isEnabled() && jwtService.isValid(token, user))
                {
                    var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            catch (JwtException | IllegalArgumentException | AuthenticationException ex)
            {
                // ungültig/abgelaufen/User weg -> unauthentifiziert weiter (-> 401)
            }
        }

        chain.doFilter(request, response);
    }

    private String readAccessCookie(HttpServletRequest request)
    {
        if (request.getCookies() == null)
        {
            return null;
        }
        for (Cookie c : request.getCookies())
        {
            if (AuthCookieService.ACCESS_COOKIE.equals(c.getName()))
            {
                return c.getValue();
            }
        }
        return null;
    }
}