package io.github.fabricetiennette.radiofy.backend.error;

import java.time.Instant;

public record ErrorResponse(
        String error,     // ex: "Bad Request", "Unauthorized"
        String code,      // ex: "INVALID_CREDENTIALS"
        String message,   // ex: "Invalid email or password."
        Instant timestamp,
        String path       // ex: "/v1/auth/login"
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse("Error", code, message, Instant.now(), null);
    }

    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse("Error", code, message, Instant.now(), path);
    }

    public static ErrorResponse of(String error, String code, String message, String path) {
        return new ErrorResponse(error, code, message, Instant.now(), path);
    }
}