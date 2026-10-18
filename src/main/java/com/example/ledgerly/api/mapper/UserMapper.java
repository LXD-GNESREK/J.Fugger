package com.example.ledgerly.api.mapper;

import com.example.ledgerly.domain.entity.User;
import com.example.ledgerly.api.dto.out.UserPublic;

import org.springframework.stereotype.Component;

@Component
public class UserMapper
{
    public UserPublic toPublicDto(User user)
    {
        if(user == null)
        {
            return null;
        }

        Long managerId = user.getManager() != null ? user.getManager().getId() : null;

        return new UserPublic
                    (
                        user.getId(),
                        user.getUsername(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getRole(),
                        managerId
                    );
    }
}
