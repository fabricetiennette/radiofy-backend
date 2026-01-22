package io.github.fabricetiennette.radiofy.backend.auth.refresh.repositories;

import io.github.fabricetiennette.radiofy.backend.auth.refresh.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Find a refresh token by its SHA-256 hex hash (unique index recommended).
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke all tokens of a family by setting revokedAt for every active token in that family.
     * Returns the number of rows affected.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update RefreshToken t
              set t.revokedAt = :now
            where t.familyId = :familyId
              and t.revokedAt is null
           """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /**
     * Purge expired tokens (optional housekeeping).
     * Returns the number of rows deleted.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           delete from RefreshToken t
            where t.expiresAt <= :now
           """)
    int purgeExpired(@Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
       update RefreshToken t
          set t.usedAt = :now
        where t.id = :id
          and t.usedAt is null
          and t.revokedAt is null
       """)
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    @Query("""
       select t
       from RefreshToken t
       join fetch t.user u
       where t.tokenHash = :tokenHash
       """)
    Optional<RefreshToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
       update RefreshToken t
          set t.revokedAt = :now
        where t.user.email = :email
          and t.revokedAt is null
       """)
    int revokeByUserEmail(@Param("email") String email, @Param("now") Instant now);
}
