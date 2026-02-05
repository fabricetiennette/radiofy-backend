package io.github.fabricetiennette.radiofy.backend.auth.dtos;

public record AuthResponse(
        String tokenType,     // "Bearer"
        String accessToken,
        String refreshToken
) {
    public static AuthResponse of(String access, String refresh) {
        return new AuthResponse("Bearer", access, refresh);
    }
}

