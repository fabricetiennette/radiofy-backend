package io.github.fabricetiennette.radiofy.backend.auth.dtos;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank String refreshToken
) {}