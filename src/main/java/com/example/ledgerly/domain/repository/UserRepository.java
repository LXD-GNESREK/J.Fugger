package com.example.ledgerly.domain.repository;

import com.example.ledgerly.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
