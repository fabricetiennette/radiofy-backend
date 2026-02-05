package io.github.fabricetiennette.radiofy.backend.auth.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for email/password sign up.
 */
public record SignUpRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password
) {}