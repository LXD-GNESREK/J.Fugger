package com.example.ledgerly.domain.policy;

import com.example.ledgerly.domain.repository.ExpenseRepository;
import com.example.ledgerly.domain.entity.Expense;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("expensePolicy")
public class ExpensePolicy
{
    private final ExpenseRepository expenseRepository;

    @Autowired
    public ExpensePolicy(ExpenseRepository expenseRepository)
    {
        this.expenseRepository = expenseRepository;
    }

    public boolean isExpenseOwner(Long currentUserId, Long expenseId)
    {
        Optional<Expense> expenseOpt = expenseRepository.findById(expenseId);
        if(expenseOpt.isEmpty() || expenseOpt.get().getCreatedBy() == null)
        {
            return false;
        }
        return expenseOpt.get().getCreatedBy().getId().equals(currentUserId);
    }

    public boolean isManagerOfExpenseOwner(long managerId, long expenseId)
    {
        Optional<Expense> expenseOpt = expenseRepository.findById(expenseId);
        if(expenseOpt.isEmpty() || expenseOpt.get().getCreatedBy() == null || expenseOpt.get().getCreatedBy().getManager() == null)
        {
            return false;
        }
        return expenseOpt.get().getCreatedBy().getManager().getId().equals(managerId);
    }

    public boolean isManagerOfExpenseOwner(long managerId, Expense expense)
    {
        if(expense == null || expense.getCreatedBy() == null || expense.getCreatedBy().getManager() == null)
        {
            return false;
        }
        return expense.getCreatedBy().getManager().getId().equals(managerId);
    }
}