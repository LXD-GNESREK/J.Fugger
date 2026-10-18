package com.example.ledgerly.application.service;

import com.example.ledgerly.domain.repository.ExpenseRepository;
import com.example.ledgerly.api.mapper.ExpenseMapper;
import com.example.ledgerly.api.dto.out.ExpensePublic;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExpenseService
{
    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;

    public ExpenseService(ExpenseRepository expenseRepository, ExpenseMapper expenseMapper)
    {
        this.expenseRepository = expenseRepository;
        this.expenseMapper = expenseMapper;
    }

    @Transactional(readOnly = true)
    public List<ExpensePublic> getTeamExpenses(Long managerId)
    {
        return expenseRepository.findExpensesByManagerId(managerId).stream().map(expenseMapper::toPublicDto).toList();
    }
}
