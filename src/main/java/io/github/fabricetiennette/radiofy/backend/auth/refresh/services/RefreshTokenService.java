package io.github.fabricetiennette.radiofy.backend.auth.refresh.services;

import io.github.fabricetiennette.radiofy.backend.auth.refresh.entities.RefreshToken;
import io.github.fabricetiennette.radiofy.backend.auth.refresh.repositories.RefreshTokenRepository;
import io.github.fabricetiennette.radiofy.backend.user.entities.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    // Default duration: 4 days (can be overridden via application.yml)
    @Value("${security.jwt.refresh.lifetime:PT96H}")
    private Duration refreshLifetime;

    private static final SecureRandom RNG = new SecureRandom();

    /**
     * Issues a new refresh token for the given user, stores it in the database (hashed),
     * and returns the raw token to be sent back to the client. Never store or log the raw token.
     */
    @Transactional
    public String issueInitialRefreshToken(UserAccount user, String ip, String userAgent) {
        // 1) Generate a strong RAW token (256-bit, Base64URL without padding)
        String rawToken = generateRawToken();

        // 2) Compute the token SHA-256 hex
        String tokenHash = sha256Hex(rawToken);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshLifetime);

        // 3) Create the persisted entity (familyId assigned in @PrePersist if null)
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .ipAddress(ip)
                .userAgent(userAgent)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(entity);
        return rawToken; // return RAW value to the client (e.g., via AuthResponse)
    }

    /**
     * Rotates a refresh token to ensure session continuity and security.
     * <p>
     * This method:
     * <ul>
     *   <li>Validates the old refresh token.</li>
     *   <li>Detects and blocks reuse attempts by revoking the entire family.</li>
     *   <li>Marks the old token as used.</li>
     *   <li>Generates and persists a new token in the same family.</li>
     * </ul>
     *
     * @param oldRawToken The previous (raw) refresh token provided by the client.
     * @param ip The user's current IP address.
     * @param userAgent The user's User-Agent string.
     * @return The new raw refresh token to send back to the client.
     * @throws SecurityException If a reuse attempt is detected.
     */
    @Transactional
    public String rotateRefreshToken(String oldRawToken, String ip, String userAgent) {
        String oldHash = sha256Hex(oldRawToken);
        RefreshToken oldToken = refreshTokenRepository
                .findByTokenHash(oldHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired refresh token"));

        // 1) Atomically mark the old token as used.
        // If already used or revoked, the update affects 0 rows → reuse/revoked detected.
        Instant now = Instant.now();
        int updated = refreshTokenRepository.markUsed(oldToken.getId(), now);
        if (updated != 1) {
            // Reuse or revoked detected: revoke the whole family and stop here.
            refreshTokenRepository.revokeFamily(oldToken.getFamilyId(), now);
            throw new SecurityException("Detected refresh token reuse or revoked token. All tokens in the family have been revoked.");
        }

        // 2) Issue a new token within the same family
        String newRawToken = generateRawToken();
        String newHash = sha256Hex(newRawToken);
        Instant expiresAt = now.plus(refreshLifetime);

        RefreshToken newToken = RefreshToken.builder()
                .user(oldToken.getUser())
                .familyId(oldToken.getFamilyId())
                .parentId(oldToken.getId())
                .tokenHash(newHash)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .ipAddress(ip)
                .userAgent(userAgent)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(newToken);
        return newRawToken; // raw value returned to the client
    }

    /**
     * Revokes the entire family of refresh tokens derived from the given raw token.
     * <p>
     * Typical use cases:
     * <ul>
     *   <li>Password reset or account recovery: invalidate every active session.</li>
     *   <li>Suspicious activity or detected compromise.</li>
     *   <li>Manual "log out from all devices".</li>
     * </ul>
     *
     * <p><b>Behavior:</b>
     * <ul>
     *   <li>Finds the token by its hash; throws if not found.</li>
     *   <li>Revokes every active token sharing the same {@code familyId}.</li>
     *   <li>Returns the number of rows affected (revoked tokens count).</li>
     * </ul>
     *
     * @param rawToken The raw refresh token provided by the client.
     * @return The number of tokens revoked in the family.
     * @throws IllegalArgumentException If the token does not exist.
     */
    @Transactional
    public int revokeFamilyByRawToken(String rawToken) {
        String hash = sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or unknown refresh token"));
        return refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
    }

    /**
     * Validates a raw refresh token and returns the persisted entity if it is still valid.
     * Uses a JOIN FETCH to eagerly load the associated User to avoid LazyInitializationException.
     */
    @Transactional
    public RefreshToken validateRefreshToken(String rawToken) {
        String hash = sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository
                .findByTokenHashWithUser(hash) // JOIN FETCH: user is initialized
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        Instant now = Instant.now();

        // Expiration check
        if (token.getExpiresAt() != null && !now.isBefore(token.getExpiresAt())) {
            throw new SecurityException("Refresh token has expired");
        }

        // Revocation check
        if (token.getRevokedAt() != null) {
            throw new SecurityException("Refresh token has been revoked");
        }

        // Reuse detection: if already used, revoke the whole family and block
        if (token.getUsedAt() != null) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new SecurityException("Detected refresh token reuse. All tokens revoked");
        }

        // No manual touch of token.getUser() needed anymore
        return token;
    }

    /**
     * Periodically purges expired refresh tokens from the database.
     * Runs daily at 03:00 server time. Uses a configurable cron if provided.
     */
    @Scheduled(cron = "${security.jwt.refresh.purge.cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredTokens() {
        refreshTokenRepository.purgeExpired(Instant.now());
    }

    /**
     * Revokes all non-revoked refresh tokens for the given user email.
     * Safe to call multiple times (idempotent).
     */
    @Transactional
    public void revokeAllForUserEmail(String email) {
        refreshTokenRepository.revokeByUserEmail(email, Instant.now());
    }

    /* -------------------- helpers -------------------- */

    private String generateRawToken() {
        byte[] bytes = new byte[32]; // 256 bits
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute SHA-256", e);
        }
    }
}

