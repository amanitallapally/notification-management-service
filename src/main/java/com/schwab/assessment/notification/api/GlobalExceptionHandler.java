package com.schwab.assessment.notification.api;

import com.schwab.assessment.notification.domain.AuditAction;
import com.schwab.assessment.notification.exception.NotificationNotFoundException;
import com.schwab.assessment.notification.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps exceptions to HTTP responses and - for submission rejections - records
 * the {@code NOTIFICATION_REJECTED} audit event required by 4.9. Rejections
 * happen before a {@code NotificationEntity} exists, so they are logged
 * under a standalone reference id (returned in the response body) rather
 * than a real notification id; they are not retrievable via
 * {@code GET /notifications/{id}/audit} for that reason (documented in
 * memory-bank/decisions.md).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotificationNotFoundException ex) {
        log.debug("Notification not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        String rejectionReference = recordRejection("validation_failed:" + fieldErrors.keySet());
        log.warn("Rejected submission reference={} fieldErrors={}", rejectionReference, fieldErrors.keySet());

        Map<String, Object> body = errorBody("Validation failed");
        body.put("fieldErrors", fieldErrors);
        body.put("rejectionReference", rejectionReference);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String rejectionReference = recordRejection(ex.getMessage());
        log.warn("Rejected submission reference={} reason={}", rejectionReference, ex.getMessage());

        Map<String, Object> body = errorBody(ex.getMessage());
        body.put("rejectionReference", rejectionReference);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Covers malformed JSON and invalid enum literals (e.g. {@code "severity":
     * "NOT_A_SEVERITY"}), which Jackson rejects during deserialization before
     * bean validation ever runs - so {@link MethodArgumentNotValidException}
     * never fires for them. The raw parser message is not echoed back (it can
     * include internal class/field details); only a generic message plus the
     * audited rejection reference is returned.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        String rejectionReference = recordRejection("unreadable_request_body");
        log.warn("Rejected unreadable request body reference={}", rejectionReference);

        Map<String, Object> body = errorBody("Request body is malformed or contains invalid field values");
        body.put("rejectionReference", rejectionReference);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private String recordRejection(String reason) {
        String reference = "rej_" + UUID.randomUUID();
        auditService.record(reference, AuditAction.NOTIFICATION_REJECTED, reason);
        return reference;
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("message", message);
        return body;
    }
}

