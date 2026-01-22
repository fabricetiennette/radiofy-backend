package io.github.fabricetiennette.radiofy.backend.auth.otp.controllers;


import io.github.fabricetiennette.radiofy.backend.auth.otp.services.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
     * Request a password-reset OTP to be sent to the email.
     * Always returns 204 to avoid user enumeration.
     * If security.otp.echo=true, returns 200 {"code": "..."} (dev convenience only).
     */
    @PostMapping("/forgot")
    public ResponseEntity<?> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        String code = service.requestPasswordReset(req.email());
        if (echoOtp) {
            return ResponseEntity.ok(Map.of("code", code));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Reset password by supplying the OTP code received by email/SMS.
     * Returns 204 on success. On invalid/expired code returns 400.
     */
    @PostMapping("/reset")
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
            // 4–10 digits; adjust if you configured a different length
            @NotBlank @Pattern(regexp = "\\d{4,10}") String code,
            @NotBlank String newPassword
    ) { }

    // ---------- Local exception mapping (simple JSON errors) ----------

    @ExceptionHandler(PasswordResetService.InvalidOtp.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOtp() {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_otp",
                "message", "The code is invalid or expired."
        ));
    }

    @ExceptionHandler(PasswordResetService.TooManyRequests.class)
    public ResponseEntity<Map<String, Object>> handleTooMany(PasswordResetService.TooManyRequests ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "too_many_requests",
                "message", ex.getMessage()
        ));
    }
}
