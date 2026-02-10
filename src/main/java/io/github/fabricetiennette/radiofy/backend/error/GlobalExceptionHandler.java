package io.github.fabricetiennette.radiofy.backend.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.security.core.AuthenticationException;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");

        return new ErrorResponse("Bad Request", "VALIDATION_ERROR", msg, Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NoSuchElementException ex, HttpServletRequest req) {
        return new ErrorResponse("Not Found", "NOT_FOUND", ex.getMessage(), Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(DataIntegrityViolationException ex, HttpServletRequest req) {
        return new ErrorResponse("Conflict", "CONFLICT", "Constraint violated", Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handle500(Exception ex, HttpServletRequest req) {
        return new ErrorResponse("Internal Server Error", "UNEXPECTED", "An unexpected error occurred", Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("Validation error");
        return new ErrorResponse("Bad Request", "VALIDATION_ERROR", msg, Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return new ErrorResponse("Not Found", "NOT_FOUND", ex.getMessage(), Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "Unauthorized",
                "code", "INVALID_CREDENTIALS",
                "message", "Invalid email or password.",
                "timestamp", Instant.now().toString(),
                "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "Unauthorized",
                "code", "AUTHENTICATION_FAILED",
                "message", "Authentication failed.",
                "timestamp", Instant.now().toString(),
                "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        // Map refresh-token validation failures to 401 (not 500).
        if ("Invalid refresh token".equals(ex.getMessage())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized",
                    "code", "INVALID_REFRESH_TOKEN",
                    "message", "Invalid refresh token.",
                    "timestamp", Instant.now().toString(),
                    "path", request.getRequestURI()
            ));
        }

        // Default for other IllegalArgumentException cases.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Bad Request",
                "code", "INVALID_ARGUMENT",
                "message", ex.getMessage() != null ? ex.getMessage() : "Invalid request.",
                "timestamp", Instant.now().toString(),
                "path", request.getRequestURI()
        ));
    }
}
