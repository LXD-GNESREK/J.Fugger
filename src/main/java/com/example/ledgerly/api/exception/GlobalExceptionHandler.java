package com.example.ledgerly.api.exception;

import com.example.ledgerly.domain.exception.AccessNotAllowedException;
import com.example.ledgerly.domain.exception.ExpenseNotFoundException;
import com.example.ledgerly.domain.exception.InvalidStatusTransitionException;
import com.example.ledgerly.domain.exception.UserNotFoundException;
import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String sanitiseForLog(String input)
    {
        if(input == null) return null;
        return input.replaceAll("[\r\n]", "_");
    }

    @ExceptionHandler
    @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS", justification = "CRLF handled by Logback configuration; stack traces must be logged.")
    public ProblemDetail handleAllUncaughtExceptions(Exception ex)
    {
        String errorID = UUID.randomUUID().toString();

        log.error("Unhandled exception occurred. Error ID: " + sanitiseForLog(errorID), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail
        (
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected system error occurred. Please contact support with Error ID: " + errorID
        );

        problem.setTitle("Internal Server Error");
        problem.setProperty("errorID", errorID);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex)
    {
        log.warn("Zugriff durch Method Security verweigert");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        problem.setTitle("Access Denied");
        return problem;
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    public ProblemDetail handleExpenseNotFound(ExpenseNotFoundException ex)
    {
        log.warn("Expense lookup failed: {}", sanitiseForLog(ex.getMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested expense could not be found.");
        problem.setTitle("Resource Not Found");
        return problem;
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidStatusTransition(InvalidStatusTransitionException ex)
    {
        log.warn("Invalid state transition attempt: {}", sanitiseForLog(ex.getMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The requested action cannot be performed in the current state.");
        problem.setTitle("State Conflict");
        return problem;
    }

    @ExceptionHandler(AccessNotAllowedException.class)
    public ProblemDetail handleAccessNotAllowed(AccessNotAllowedException ex)
    {
        log.warn("Unauthorised access attempt: {}", sanitiseForLog(ex.getMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        problem.setTitle("Access Denied");
        return problem;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex)
    {
        log.warn("User lookup failed: {}", sanitiseForLog(ex.getMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested user or assigned manager could not be found.");
        problem.setTitle("User Not Found");
        return problem;
    }
}
