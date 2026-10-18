package com.example.ledgerly.security;

import com.example.ledgerly.domain.entity.User;
import com.example.ledgerly.domain.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider
{

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository)
    {
        this.userRepository = userRepository;
    }

    public Long getCurrentUserId()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated())
        {
            throw new IllegalStateException("No authenticated user found in security context.");
        }

        String username = null;
        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails)
        {
            username = userDetails.getUsername();
        }
        else if (principal instanceof String principalString)
        {
            username = principalString;
        }

        if (username != null)
        {
            User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
            return user.getId();
        }

        try
        {
            return Long.parseLong(authentication.getName());
        }
        catch (NumberFormatException e)
        {
            throw new IllegalStateException("Could not extract user ID from authentication token.");
        }
    }
}