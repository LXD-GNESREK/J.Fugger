package com.example.ledgerly.domain.exception;

// Unerlaubter Statuswechsel, z. B. APPROVED -> DRAFT
public class InvalidStatusTransitionException extends RuntimeException {
}
