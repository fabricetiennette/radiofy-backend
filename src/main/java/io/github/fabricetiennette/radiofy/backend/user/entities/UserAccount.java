package io.github.fabricetiennette.radiofy.backend.user.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@AllArgsConstructor
@NoArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "legacy_firebase_uid", length = 128, unique = true)
    private String legacyFirebaseUid;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 150)
    private String displayName;

    @Column(length = 300)
    private String photoUrl;

    @Column(nullable = false, length = 30)
    private String role = "USER";

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }
}
