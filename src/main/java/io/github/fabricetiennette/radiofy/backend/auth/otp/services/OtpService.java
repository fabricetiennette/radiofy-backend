package io.github.fabricetiennette.radiofy.backend.auth.otp.services;

import io.github.fabricetiennette.radiofy.backend.auth.OtpHasher;
import io.github.fabricetiennette.radiofy.backend.auth.otp.entities.EmailOtp;
import io.github.fabricetiennette.radiofy.backend.auth.otp.repositories.EmailOtpCodeRepository;
import io.github.fabricetiennette.radiofy.backend.user.repositoties.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles OTP verification for email confirmation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final EmailOtpCodeRepository otpRepo;
    private final UserAccountRepository userRepo;
    private final SmtpEmailSender smtpEmailSender;
    private final OtpHasher otpHasher;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /**
     * Verify the OTP code for email verification and mark the user as verified.
     * Throws typed runtime exceptions consumed by the controller.
     */
    @Transactional
    public void verifyEmailCode(String email, String code) {
        var now = Instant.now();

        var otp = otpRepo
                .findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        email, EmailOtp.OtpPurpose.EMAIL_VERIFY
                )
                .orElseThrow(OtpNotFoundException::new);

        if (otp.getExpiresAt().isBefore(now)) {
            throw new OtpExpiredException();
        }

        final int MAX_ATTEMPTS = 5;
        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new OtpInvalidException("Too many attempts");
        }

        // verify
        boolean ok = otpHasher.matches(code, otp.getCodeHash());

        if (!ok) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepo.save(otp);
            throw new OtpInvalidException("Invalid code");
        }

        var user = userRepo.findByEmail(email)
                .orElseThrow(() -> new OtpInvalidException("User not found"));

        if (user.getEmailVerifiedAt() != null) {
            throw new AlreadyVerifiedException();
        }

        // success
        user.setEmailVerifiedAt(now);
        otp.setConsumedAt(now);

        userRepo.save(user);
        otpRepo.save(otp);
    }

    /**
     * Optional: housekeeping endpoint/job – purge used or expired tokens.
     */
    @Transactional
    public int purgeExpiredOrConsumed() {
        return otpRepo.deleteExpiredOrConsumed(Instant.now());
    }

    // ---- Exceptions (runtime, simple) ----
    public static class OtpNotFoundException extends RuntimeException {
        public OtpNotFoundException() { super("OTP code not found or already used"); }
    }

    public static class OtpExpiredException extends RuntimeException {
        public OtpExpiredException() { super("OTP code expired"); }
    }

    public static class OtpInvalidException extends RuntimeException {
        public OtpInvalidException(String msg) { super(msg); }
    }

    public static class AlreadyVerifiedException extends RuntimeException {
        public AlreadyVerifiedException() { super("Email already verified"); }
    }

    @Transactional
    public String issueEmailVerificationCode(String email) {
        final var purpose = EmailOtp.OtpPurpose.EMAIL_VERIFY;
        final Instant now = Instant.now();
        final int ttlMinutes = 15;

        // Cap active unconsumed, unexpired codes
        long activeCount = otpRepo.countByEmailAndPurposeAndConsumedAtIsNullAndExpiresAtAfter(
                email, purpose, now
        );
        if (activeCount >= 3) {
            throw new OtpInvalidException("Too many active codes");
        }

        // Generate 6-digit numeric code (plaintext)
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));

        // Hash it for storage
        String codeHash = otpHasher.hash(code);

        var otp = EmailOtp.builder()
                .email(email)
                .codeHash(codeHash)
                .purpose(purpose)
                .expiresAt(now.plus(Duration.ofMinutes(ttlMinutes)))
                .createdAt(now)
                .attempts(0)
                .build();

        otpRepo.save(otp);

        // Never log the plaintext code in prod
        // log.info("Verification code issued for {}", email);

        smtpEmailSender.sendVerificationCodeEmail(email, code, ttlMinutes);

        // Dev only: return plaintext code
        if ("local".equals(activeProfile)) {
            return code;
        }
        return null;
    }
}
