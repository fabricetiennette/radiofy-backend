package io.github.fabricetiennette.radiofy.backend.auth.controllers;


import io.github.fabricetiennette.radiofy.backend.auth.otp.services.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Validated
public class PasswordResetController {

    private final PasswordResetService service;

    // optional: in dev you can echo the OTP in the response for easy testing
    @Value("${security.otp.echo:false}")
    private boolean echoOtp;

    // ---------- Endpoints ----------

    /**
     * Request a password-reset OTP to be sent to the email (6 digits).
     * Always returns 204 to avoid user enumeration.
     * If security.otp.echo=true, returns 200 {"code": "..."} (dev convenience only).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        String code = service.requestPasswordReset(req.email());
        if (echoOtp) {
            return ResponseEntity.ok(Map.of("code", code));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Reset password by supplying the 6-digit OTP code received by email/SMS.
     * Returns 204 on success. On invalid/expired code returns 422.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        service.resetPasswordWithCode(req.email(), req.code(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    // ---------- DTOs ----------

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) { }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            // 6 digits (standard OTP)
            @NotBlank @Pattern(regexp = "\\d{6}") String code,
            @NotBlank
            @Size(min = 8, max = 72) // 72 is a common upper bound (e.g., bcrypt input size considerations)
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                    message = "Password must contain at least one lowercase letter, one uppercase letter, and one digit"
            )
            String newPassword
    ) { }

    // ---------- Local exception mapping (simple JSON errors) ----------

    @ExceptionHandler(PasswordResetService.InvalidOtp.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOtp() {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "type", "https://errors.radiofy.local/invalid-otp",
                "title", "Invalid OTP",
                "status", 422,
                "detail", "The code is invalid or expired."
        ));
    }

    @ExceptionHandler(PasswordResetService.TooManyRequests.class)
    public ResponseEntity<Map<String, Object>> handleTooMany(PasswordResetService.TooManyRequests ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "type", "https://errors.radiofy.local/too-many-requests",
                "title", "Too many requests",
                "status", 429,
                "detail", ex.getMessage()
        ));
    }
}
