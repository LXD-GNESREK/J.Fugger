package com.example.ledgerly.api.mapper;

import com.example.ledgerly.api.dto.out.ExpensePublic;
import com.example.ledgerly.domain.entity.Expense;

import org.springframework.stereotype.Component;

@Component
public class ExpenseMapper
{
    public ExpensePublic toPublicDto(Expense expense)
    {
        if(expense == null)
        {
            return null;
        }

        Long createdById = (expense.getCreatedBy() != null) ? expense.getCreatedBy().getId() : null;
        Long reviewedById = (expense.getReviewedBy() != null) ? expense.getReviewedBy().getId() : null;

        return new ExpensePublic
                    (
                        expense.getId(),
                        expense.getDescription(),
                        expense.getAmount(),
                        expense.getExpenseDate(),
                        expense.getCategory(),
                        expense.getStatus(),
                        createdById,
                        reviewedById,
                        expense.getRejectionReason(),
                        expense.getCreatedAt(),
                        expense.getSubmittedAt(),
                        expense.getReviewedAt(),
                        expense.getVersion()
                    );
    }
}
