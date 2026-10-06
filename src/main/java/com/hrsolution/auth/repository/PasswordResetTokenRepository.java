package com.hrsolution.auth.repository;

import com.hrsolution.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @EntityGraph(attributePaths = {"user"})
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates outstanding reset tokens before issuing a new one. Important
     * for more than tidiness: if a user requests two resets, only the newest
     * link should work, so an older email that may have been intercepted is
     * already dead.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken t
            set t.usedAt = :now
            where t.user.id = :userId and t.usedAt is null
            """)
    int invalidateOutstandingForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    @Query("""
            select count(t) from PasswordResetToken t
            where t.user.id = :userId and t.createdAt > :since
            """)
    long countRecentForUser(@Param("userId") Long userId, @Param("since") Instant since);
}
