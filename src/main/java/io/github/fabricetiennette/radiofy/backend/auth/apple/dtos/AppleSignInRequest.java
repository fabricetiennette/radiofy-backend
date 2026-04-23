package io.github.fabricetiennette.radiofy.backend.auth.apple.dtos;

public record AppleSignInRequest(
        String idToken,
        String givenName,
        String familyName
) {}
