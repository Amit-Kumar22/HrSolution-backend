package com.hrsolution.auth.repository;

import com.hrsolution.auth.entity.RefreshToken;
import com.hrsolution.auth.entity.RevokedReason;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Looks up by hash. Returns revoked and expired rows too - the caller must
     * see a revoked row in order to detect reuse, so filtering them here would
     * silently disable theft detection and make a replayed token look merely
     * unknown.
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Active sessions for the sessions screen, newest first. */
    @Query("""
            select t from RefreshToken t
            where t.user.id = :userId
              and t.revokedAt is null
              and t.expiresAt > :now
            order by t.createdAt desc
            """)
    List<RefreshToken> findActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("""
            select t from RefreshToken t
            where t.familyId = :familyId
              and t.revokedAt is null
            """)
    List<RefreshToken> findActiveByFamilyId(@Param("familyId") String familyId);

    /**
     * Bulk revoke, for logout-all, password change and account disable.
     * A single UPDATE rather than a load-mutate-save loop; it bypasses the
     * entity callbacks, which is fine because nothing is audited per row here -
     * one audit event is written for the whole operation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
            set t.revokedAt = :now, t.revokedReason = :reason
            where t.user.id = :userId and t.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("reason") RevokedReason reason,
                         @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
            set t.revokedAt = :now, t.revokedReason = :reason
            where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") String familyId,
                     @Param("reason") RevokedReason reason,
                     @Param("now") Instant now);

    /**
     * Housekeeping for the scheduled purge. Only removes rows that are both
     * expired and past the retention window, so recently-revoked tokens stay
     * available for incident investigation.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    long countByUserIdAndRevokedAtIsNull(Long userId);
}
