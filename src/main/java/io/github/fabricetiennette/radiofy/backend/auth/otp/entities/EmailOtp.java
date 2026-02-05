package io.github.fabricetiennette.radiofy.backend.auth.otp.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps the V3__create_email_otp_code.sql table.
 * Partial unique index (active code per email/purpose) is created by Flyway.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "email_otps",
        indexes = {
                @Index(name = "idx_email_otps_email_purpose_created", columnList = "email,purpose,created_at")
        }
)
public class EmailOtp {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    /**
     * Store ONLY a hash of the OTP code (never store the OTP in plaintext).
     */
    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 50)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Null => not consumed yet. Non-null => consumed.
     */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "request_ip", length = 64)
    private String requestIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public enum OtpPurpose {
        EMAIL_VERIFY,
        PASSWORD_RESET
        // Add later if needed: LOGIN, LOGIN_2FA
    }
}
