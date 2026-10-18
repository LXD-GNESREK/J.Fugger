package com.example.ledgerly.api.dto.out;

import com.example.ledgerly.domain.model.ExpenseCategory;
import com.example.ledgerly.domain.model.ExpenseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpensePublic
(
        Long id,
        String description,
        BigDecimal amount,
        LocalDate expenseData,
        ExpenseCategory category,
        ExpenseStatus status,
        Long createdById,
        Long reviewedById,
        String rejectionReason,
        Instant createdAt,
        Instant submittedAt,
        Instant reviewedAt,
        Long version
) {}
