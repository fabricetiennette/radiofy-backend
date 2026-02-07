package io.github.fabricetiennette.radiofy.backend.auth.otp.repositories;

import io.github.fabricetiennette.radiofy.backend.auth.otp.entities.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpCodeRepository extends JpaRepository<EmailOtp, UUID> {

    Optional<EmailOtp> findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email,
            EmailOtp.OtpPurpose purpose
    );

    // Latest code for an email/purpose (regardless of consumed/expired)
    Optional<EmailOtp> findTopByEmailAndPurposeOrderByCreatedAtDesc(
            String email,
            EmailOtp.OtpPurpose purpose
    );

    long countByEmailAndPurposeAndConsumedAtIsNullAndExpiresAtAfter(
            String email,
            EmailOtp.OtpPurpose purpose,
            Instant now
    );

    // Housekeeping: remove expired or already consumed codes (optional)
    @Modifying
    @Query("delete from EmailOtp e where e.expiresAt < :now or e.consumedAt is not null")
    int deleteExpiredOrConsumed(@Param("now") Instant now);
}
