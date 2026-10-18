package com.example.ledgerly.api.dto.out;

import com.example.ledgerly.domain.model.Role;

public record UserPublic
(
        Long id,
        String username,
        String firstName,
        String lastName,
        Role role,
        Long managerId
) {}
