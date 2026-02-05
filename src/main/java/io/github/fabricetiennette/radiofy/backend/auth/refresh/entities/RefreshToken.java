package io.github.fabricetiennette.radiofy.backend.auth.refresh.entities;

import io.github.fabricetiennette.radiofy.backend.user.entities.UserAccount;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent model for refresh tokens with rotation & reuse detection.
 * We store only a SHA-256 hex hash of the raw token (tokenHash).
 */
@Getter@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "refresh_tokens_token_hash_key", columnNames = "token_hash")
        }
)
public class RefreshToken implements Persistable<UUID> {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_tokens_user")
    )
    private UserAccount user;

    /** Same family across a rotation chain (new token issued on each refresh). */
    @Column(name = "family_id", nullable = false, columnDefinition = "uuid")
    private UUID familyId;

    /** Optional pointer to previous token in the chain (for audits). */
    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    /** SHA-256 hex (64 chars) of the refresh token. Never store the raw token. */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the token is successfully exchanged once (rotation). */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Set if reuse is detected or tokens are explicitly revoked. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Optional context for audit; mapped as text IP (Postgres inet in DB). */
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    /** Optional context for audit. */
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    @PostLoad
    @PostPersist
    private void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    // 4) Ensure this getter exists (needed by Persistable<UUID>)
    @Override
    public UUID getId() {
        return this.id;
    }

    /** Convenience: is this token currently valid and not consumed? */
    @Transient
    public boolean isActive() {
        return usedAt == null
                && revokedAt == null
                && expiresAt != null
                && expiresAt.isAfter(Instant.now());
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (issuedAt == null)   issuedAt = now;
        if (createdAt == null)  createdAt = now;
        if (familyId == null)   familyId = UUID.randomUUID();
    }
}