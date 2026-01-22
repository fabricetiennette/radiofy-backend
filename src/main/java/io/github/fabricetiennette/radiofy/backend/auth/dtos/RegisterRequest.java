package io.github.fabricetiennette.radiofy.backend.auth.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {}
