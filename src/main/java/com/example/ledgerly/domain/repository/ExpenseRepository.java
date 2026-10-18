package com.example.ledgerly.domain.repository;

import com.example.ledgerly.domain.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>
{

    List<Expense> findByCreatedById(Long createdById);

    @Query("SELECT e FROM Expense e WHERE e.createdBy.manager.id = :managerId")
    List<Expense> findExpensesByManagerId(@Param("managerId") Long managerId);
}