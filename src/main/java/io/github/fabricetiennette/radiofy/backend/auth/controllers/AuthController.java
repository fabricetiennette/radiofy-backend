package io.github.fabricetiennette.radiofy.backend.auth.controllers;

import io.github.fabricetiennette.radiofy.backend.auth.dtos.AuthResponse;
import io.github.fabricetiennette.radiofy.backend.auth.dtos.LoginRequest;
import io.github.fabricetiennette.radiofy.backend.auth.dtos.RefreshRequest;
import io.github.fabricetiennette.radiofy.backend.auth.dtos.RegisterRequest;
import io.github.fabricetiennette.radiofy.backend.auth.jwt.JwtService;
import io.github.fabricetiennette.radiofy.backend.auth.otp.services.OtpService;
import io.github.fabricetiennette.radiofy.backend.auth.refresh.entities.RefreshToken;
import io.github.fabricetiennette.radiofy.backend.auth.refresh.services.RefreshTokenService;
import io.github.fabricetiennette.radiofy.backend.user.services.UserService;
import io.github.fabricetiennette.radiofy.backend.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.mail.MailException;

import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final UserService userService;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;

    private record VerifyEmailRequest(String email, String code) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest httpRequest) {
        try {
            // 1) Create the user account
            userService.register(req.email(), req.password());

            // 2) Immediately issue a verification code (DEV ONLY: returned in body)
            // Issue the email
            otpService.issueEmailVerificationCode(req.email());

            // 3) Generate tokens
            //    - Access: JWT signed for the email
            //    - Refresh: DB-backed random token (raw returned to client, hash stored in DB)
            String access = jwt.generateAccess(req.email(), Map.of("typ", "access"));

            var u = userService.findByEmail(req.email())
                    .orElseThrow(() -> new IllegalStateException("User not found after registration"));
            String ip = httpRequest.getRemoteAddr();
            String userAgent = httpRequest.getHeader("User-Agent");
            String refresh = refreshTokenService.issueInitialRefreshToken(u, ip, userAgent);

            // 4) Return tokens and (DEV ONLY) the verification code
            return ResponseEntity.ok(Map.of(
                    "access", access,
                    "refresh", refresh
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ErrorResponse.of("EMAIL_ALREADY_EXISTS", e.getMessage())
            );
        } catch (MailException e) {
            log.warn("Failed to send verification email to {}", req.email(), e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                    ErrorResponse.of("EMAIL_DELIVERY_FAILED", "Unable to deliver verification email. Please check the email address.")
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpRequest) {
        var userOpt = userService.findByEmail(req.email());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ErrorResponse.of("Unauthorized", "INVALID_CREDENTIALS", "Invalid email or password.", httpRequest.getRequestURI()));
        }

        var u = userOpt.get();
        if (!u.isEmailVerified()) {
            return ResponseEntity.status(403).body(
                    ErrorResponse.of(
                            "Forbidden",
                            "EMAIL_NOT_VERIFIED",
                            "Email is not verified.",
                            "/api/v1/auth/verify-email/resend"
                    )
            );
        }

        var authToken = new UsernamePasswordAuthenticationToken(req.email(), req.password());
        authManager.authenticate(authToken);

        // Issue access token (JWT)
        String access = jwt.generateAccess(req.email(), Map.of("typ","access"));

        // Issue refresh token via DB-backed service (random raw token, hashed in DB)
        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        String refresh = refreshTokenService.issueInitialRefreshToken(u, ip, userAgent);

        return ResponseEntity.ok(AuthResponse.of(access, refresh));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshTokens(@Valid @RequestBody RefreshRequest request,
                                                      HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        // 1) Validate the old refresh token
        RefreshToken validToken = refreshTokenService.validateRefreshToken(request.refreshToken());

        // 2) Rotate the token (invalidate the old one, create a new one)
        String newRefresh = refreshTokenService.rotateRefreshToken(request.refreshToken(), ip, userAgent);

        // 3) Issue a new access token
        String newAccess = jwt.generateAccess(validToken.getUser().getEmail(), Map.of("typ","access"));

        return ResponseEntity.ok(AuthResponse.of(newAccess, newRefresh));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestBody VerifyEmailRequest req) {
        // 1) Validate input (basic null/blank)
        if (req.email() == null || req.email().isBlank() || req.code() == null || req.code().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // 2) Delegate to service that:
        //    - fetches OTP by email
        //    - checks code, expiry, attempts
        //    - marks user as emailVerified = true on success
        //    - deletes/invalidates OTP
        //    - throws a specific exception on failure
        try {
            otpService.verifyEmailCode(req.email(), req.code());
            return ResponseEntity.noContent().build(); // 204 on success
        } catch (OtpService.OtpNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // no OTP found for email
        } catch (OtpService.OtpExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).build(); // expired code
        } catch (OtpService.OtpInvalidException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build(); // wrong code
        } catch (OtpService.AlreadyVerifiedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // email already verified
        }
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<Void> resendVerification(@RequestBody Map<String, String> body) {
        var email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        userService.findByEmail(email).ifPresent(u -> {
            if (!u.isEmailVerified()) {
                otpService.issueEmailVerificationCode(email);
            }
        });

        // Always 204 to avoid user enumeration
        return ResponseEntity.noContent().build();
    }
}
