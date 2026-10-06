package com.hrsolution.user.repository;

import com.hrsolution.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * Login lookup. The entity graph fetches roles <em>and</em> their
     * permissions in one query, because the access token embeds both as claims
     * - without it, minting a token for a user with three roles would fire a
     * query per role (classic N+1 on the hottest path in the system).
     *
     * <p>Soft-deleted accounts are excluded: a deactivated user must not be
     * able to log in.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from User u where lower(u.email) = lower(:email) and u.deleted = false")
    Optional<User> findActiveByEmailWithRoles(@Param("email") String email);

    /**
     * Per-request lookup used while authenticating a bearer token, to confirm
     * the account is still enabled and its {@code tokenVersion} still matches.
     * Also fetches authorities, since they are rebuilt from the database rather
     * than trusted from the token.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from User u where u.id = :id and u.deleted = false")
    Optional<User> findActiveByIdWithRoles(@Param("id") Long id);

    @Query("select u from User u where lower(u.email) = lower(:email) and u.deleted = false")
    Optional<User> findActiveByEmail(@Param("email") String email);

    /**
     * Existence check for registration. Includes soft-deleted rows on purpose:
     * the unique constraint covers them, so ignoring them would turn a clear
     * 409 into a database integrity error.
     */
    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoringSoftDelete(@Param("email") String email);

    @EntityGraph(attributePaths = {"roles"})
    Page<User> findAllByDeletedFalse(Pageable pageable);
}
