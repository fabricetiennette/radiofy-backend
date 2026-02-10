package io.github.fabricetiennette.radiofy.backend.user.controller;

import io.github.fabricetiennette.radiofy.backend.auth.refresh.services.RefreshTokenService;
import io.github.fabricetiennette.radiofy.backend.user.dto.UserMeResponse;
import io.github.fabricetiennette.radiofy.backend.user.services.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class UserController {

    public final RefreshTokenService refreshTokenService;
    public final UserService userService;

    /**
     * Returns minimal profile info for the authenticated user.
     * Security note: requires a valid Access JWT; otherwise returns 401.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserMeResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // In this setup, the principal's name is the user's email
        String email = auth.getName();

        // Minimal response; add fields later as needed
        UserMeResponse body = new UserMeResponse(email, Instant.now());
        return ResponseEntity.ok(body);
    }

    /**
     * Deletes the authenticated user's account.
     * Steps: revoke all refresh tokens, then delete user (hard).
     * Idempotent: returns 204 even if the user was already deleted.
     */
    @DeleteMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<Void> deleteMe(Authentication auth) {

        // 1️⃣ Get user's email
        String email = auth.getName();

        // 2️⃣ Revoke all refresh tokens before deleting user (defense-in-depth)
        refreshTokenService.revokeAllForUserEmail(email);

        // 3️⃣ Hard delete from main table
        userService.deleteByEmail(email);

        // 4️⃣ Return 204 No Content
        return ResponseEntity.noContent().build();
    }
}
