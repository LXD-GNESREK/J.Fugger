package com.example.ledgerly.domain.repository;

import com.example.ledgerly.domain.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
}

