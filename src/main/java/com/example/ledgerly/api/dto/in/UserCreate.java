package com.example.ledgerly.api.dto.in;

import com.example.ledgerly.domain.model.Role;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserCreate
(
        @NotBlank(message = "Username is required.")
        @Size(max = 50, message = "Username cannot exceed 50 characters.")
        String username,

        @NotBlank(message = "Password is required.")
        @Size(min = 16, message = "Password must be at least 16 characters.")
        String password,

        @NotBlank(message = "First name is required.")
        @Size(max = 100, message = "First name cannot exceed 100 characters.")
        String firstName,

        @NotBlank(message = "Last name is required.")
        @Size(max = 100, message = "Last name cannot exceed 100 characters.")
        String lastName,

        @NotNull(message = "Role is required.")
        Role role,

        Long managerId
) {}
