package io.github.fabricetiennette.radiofy.backend.user.controller;

import io.github.fabricetiennette.radiofy.backend.user.dto.UserMeResponse;
import io.github.fabricetiennette.radiofy.backend.user.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class UserController {

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
}
