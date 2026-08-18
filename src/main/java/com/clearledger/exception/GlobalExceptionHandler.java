package com.clearledger.exception;

import com.clearledger.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(
            InsufficientBalanceException ex, HttpServletRequest req) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransaction(
            InvalidTransactionException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION", ex.getMessage(), req);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage(), req);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyMismatch(
            CurrencyMismatchException ex, HttpServletRequest req) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH", ex.getMessage(), req);
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountInactive(
            AccountInactiveException ex, HttpServletRequest req) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_INACTIVE", ex.getMessage(), req);
    }

    @ExceptionHandler(LedgerInvariantViolationException.class)
    public ResponseEntity<ErrorResponse> handleLedgerViolation(
            LedgerInvariantViolationException ex, HttpServletRequest req) {
        log.error("CRITICAL ledger invariant violation: {}", ex.getMessage());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "LEDGER_INVARIANT_VIOLATION", ex.getMessage(), req);
    }

    @ExceptionHandler(SettlementBatchNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSettlementBatchNotFound(
            SettlementBatchNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "SETTLEMENT_BATCH_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(DuplicateSettlementBatchException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSettlementBatch(
            DuplicateSettlementBatchException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_SETTLEMENT_BATCH", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidSettlementBatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSettlementBatch(
            InvalidSettlementBatchException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_SETTLEMENT_BATCH", ex.getMessage(), req);
    }

    @ExceptionHandler(ReconciliationMatchNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReconciliationMatchNotFound(
            ReconciliationMatchNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "RECONCILIATION_MATCH_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidResolutionActionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResolutionAction(
            InvalidResolutionActionException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_RESOLUTION_ACTION", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDateRange(
            InvalidDateRangeException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", req);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String error,
                                                 String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(), status.value(), error, message, req.getRequestURI()));
    }
}
