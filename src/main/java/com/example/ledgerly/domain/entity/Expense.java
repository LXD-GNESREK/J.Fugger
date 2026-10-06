package com.example.ledgerly.domain.entity;

import com.example.ledgerly.domain.model.ExpenseCategory;
import com.example.ledgerly.domain.model.ExpenseStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String description;

    // BigDecimal statt double: keine Rundungsfehler bei Geldbeträgen
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    // ---------- vom System gesetzt (nie aus dem Request!) ----------

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseStatus status = ExpenseStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(length = 500)
    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant submittedAt;

    private Instant reviewedAt;

    // Optimistic Locking: verhindert, dass zwei gleichzeitige Änderungen
    // sich gegenseitig überschreiben (z. B. Bearbeiten während Genehmigen)
    @Version
    private Long version;

    // Für JPA
    protected Expense() {
    }

    // createdBy kommt vom Service aus dem eingeloggten User
    public Expense(String description, BigDecimal amount, LocalDate expenseDate,
                   ExpenseCategory category, User createdBy) {
        this.description = description;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.category = category;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    // --- Inhalt bearbeiten (Prüfung "nur im Status DRAFT" kommt in domain/policy) ---

    public void updateDetails(String description, BigDecimal amount,
                              LocalDate expenseDate, ExpenseCategory category) {
        this.description = description;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.category = category;
    }

    // --- Statusübergänge (Regeln, wer wann darf, kommen in domain/policy) ---

    public void markSubmitted() {
        this.status = ExpenseStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void markApproved(User reviewer) {
        this.status = ExpenseStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    public void markRejected(User reviewer, String reason) {
        this.status = ExpenseStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.rejectionReason = reason;
        this.reviewedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public ExpenseCategory getCategory() { return category; }
    public ExpenseStatus getStatus() { return status; }
    public User getCreatedBy() { return createdBy; }
    public User getReviewedBy() { return reviewedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Long getVersion() { return version; }
}
