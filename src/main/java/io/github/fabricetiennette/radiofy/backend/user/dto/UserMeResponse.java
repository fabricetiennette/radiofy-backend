package io.github.fabricetiennette.radiofy.backend.user.dto;

import java.time.Instant;

/**
 * Minimal DTO for /v1/user/me response.
 */
public record UserMeResponse(
        String email,
        Instant serverTime
) {}
