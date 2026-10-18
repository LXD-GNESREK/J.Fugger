package com.example.ledgerly.api.controller;

import com.example.ledgerly.application.service.ExpenseService;
import com.example.ledgerly.api.dto.out.ExpensePublic;
import com.example.ledgerly.security.CurrentUserProvider;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController
{
    private final ExpenseService expenseService;
    private final CurrentUserProvider currentUserProvider;

    public ExpenseController(ExpenseService expenseService, CurrentUserProvider currentUserProvider)
    {
        this.expenseService = expenseService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/team")
    @PreAuthorize("hasAuthority('MANAGER')")
    public List<ExpensePublic> getTeamExpenses()
    {
        Long currentManagerId = currentUserProvider.getCurrentUserId();
        return expenseService.getTeamExpenses(currentManagerId);
    }
}
