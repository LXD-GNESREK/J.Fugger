package com.example.ledgerly.application.service;

import com.example.ledgerly.domain.repository.UserRepository;
import com.example.ledgerly.domain.entity.User;
import com.example.ledgerly.api.dto.in.UserCreate;
import com.example.ledgerly.domain.exception.UserNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService
{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(UserCreate request)
    {
        User manager = null;

        if(request.managerId() != null)
        {
            manager = userRepository.findById(request.managerId()).orElseThrow(() -> new UserNotFoundException("Manager with ID " + request.managerId() + " not found."));
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        User user = new User
                        (
                            request.username(),
                            hashedPassword,
                            request.firstName(),
                            request.lastName(),
                            request.role(),
                            manager
                        );

        return userRepository.save(user);
    }
}
