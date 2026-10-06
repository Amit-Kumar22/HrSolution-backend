package com.hrsolution.auth.repository;

import com.hrsolution.auth.entity.VerificationToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    @EntityGraph(attributePaths = {"user"})
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates any outstanding tokens before a new one is issued, so that
     * "resend verification" cannot leave several working links in the user's
     * inbox at once.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VerificationToken t
            set t.usedAt = :now
            where t.user.id = :userId and t.usedAt is null
            """)
    int invalidateOutstandingForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from VerificationToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    /** Throttles resends: how many were issued in the recent window. */
    @Query("""
            select count(t) from VerificationToken t
            where t.user.id = :userId and t.createdAt > :since
            """)
    long countRecentForUser(@Param("userId") Long userId, @Param("since") Instant since);
}
