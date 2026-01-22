package io.github.fabricetiennette.radiofy.backend.auth.otp.services;

import io.github.fabricetiennette.radiofy.backend.auth.OtpHasher;
import io.github.fabricetiennette.radiofy.backend.auth.otp.entities.EmailOtp;
import io.github.fabricetiennette.radiofy.backend.auth.otp.repositories.EmailOtpCodeRepository;
import io.github.fabricetiennette.radiofy.backend.user.entities.UserAccount;
import io.github.fabricetiennette.radiofy.backend.user.repositoties.UserAccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final EmailOtpCodeRepository repo;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final SmtpEmailSender smtpEmailSender;
    private final OtpHasher otpHasher;

    // --- Tunables (can be moved to @ConfigurationProperties later)
    @Value("${security.otp.length:6}")
    private int otpLength; // digits

    @Value("${security.otp.ttl-minutes:10}")
    private long ttlMinutes;

    @Value("${security.otp.throttle-seconds:60}")
    private long throttleSeconds;

    private static final SecureRandom RNG = new SecureRandom();

    /**
     * Issue a password-reset OTP for an email.
     * Behavior does NOT reveal whether the email exists (avoid user enumeration).
     *
     * @return the OTP code (for development/testing). In prod, send via email/SMS and return nothing.
     */
    @Transactional
    public String requestPasswordReset(String email) {
        final var purpose = EmailOtp.OtpPurpose.PASSWORD_RESET;
        final Instant now = Instant.now();

        // 1) Throttle: prevent spamming
        repo.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .filter(last -> last.getCreatedAt().isAfter(now.minusSeconds(throttleSeconds)))
                .ifPresent(last -> { throw new TooManyRequests("Please wait before requesting another code."); });

        // 2) Cap active codes (not consumed + not expired)
        long active = repo.countByEmailAndPurposeAndConsumedAtIsNullAndExpiresAtAfter(email, purpose, now);
        if (active >= 3) {
            throw new TooManyRequests("Too many active codes. Try again later.");
        }

        // 3) Create code + hash
        String code = generateNumericCode(otpLength);
        String codeHash = otpHasher.hash(code);

        var entity = EmailOtp.builder()
                .email(email)
                .codeHash(codeHash)
                .purpose(purpose)
                .expiresAt(now.plus(Duration.ofMinutes(ttlMinutes)))
                .attempts(0)
                .createdAt(now) // ou laisse @PrePersist
                .build();

        repo.save(entity);

        // 4) Send email (always, even if user doesn't exist => anti-enumeration is at controller level)
        smtpEmailSender.sendPasswordResetCode(email, code);

        // Dev only: return code
        return code;
    }

    /**
     * Verify an OTP and consume it. If valid, updates password hash for the user.
     * To avoid user enumeration, throws InvalidOtp for any error (expired/missing/consumed).
     */
    @Transactional
    public void resetPasswordWithCode(String email, String code, String newRawPassword) {
        final var purpose = EmailOtp.OtpPurpose.PASSWORD_RESET;
        final Instant now = Instant.now();
        final int MAX_ATTEMPTS = 5;

        // 1) Load latest active OTP (do NOT filter by code in DB)
        var record = repo
                .findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(InvalidOtp::new);

        // 2) Expiration
        if (record.getExpiresAt().isBefore(now)) {
            throw new InvalidOtp();
        }

        // 3) Attempts guard
        if (record.getAttempts() >= MAX_ATTEMPTS) {
            throw new InvalidOtp();
        }

        // 4) Verify code (hash compare)
        boolean ok = otpHasher.matches(code, record.getCodeHash());
        if (!ok) {
            record.setAttempts(record.getAttempts() + 1);
            repo.save(record);
            throw new InvalidOtp();
        }

        // 5) Consume OTP
        record.setConsumedAt(now);
        repo.save(record);

        // 6) Update password if user exists (avoid revealing existence)
        users.findByEmail(email).ifPresent(u -> updatePassword(u, newRawPassword));
    }

    // ---------- helpers ----------

    private String generateNumericCode(int len) {
        // cryptographically-strong numeric code, fixed length (leading zeros allowed)
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(RNG.nextInt(10));
        }
        return sb.toString();
    }

    private void updatePassword(UserAccount u, String raw) {
        u.setPasswordHash(encoder.encode(raw));
        users.save(u);
    }

    // ---------- exceptions (simple runtime types for controller mapping) ----------

    public static class InvalidOtp extends RuntimeException {}
    public static class TooManyRequests extends RuntimeException {
        public TooManyRequests(String msg) { super(msg); }
    }
}
