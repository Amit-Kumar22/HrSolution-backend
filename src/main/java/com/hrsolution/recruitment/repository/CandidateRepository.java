package com.hrsolution.recruitment.repository;

import com.hrsolution.recruitment.entity.Candidate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long>,
        JpaSpecificationExecutor<Candidate> {

    @EntityGraph(attributePaths = {"user", "preferredCategory", "resumeDocument"})
    @Query("select c from Candidate c where c.id = :id and c.deleted = false")
    Optional<Candidate> findActiveById(@Param("id") Long id);

    /**
     * Looks up by email, the identity key for this table.
     *
     * <p>Deliberately ignores the soft-delete flag. The database constraint on
     * email does too, so a soft-deleted candidate still owns their address; not
     * finding it here would mean every re-application from that person failed
     * with a confusing 409 from the constraint instead of being handled.
     */
    @EntityGraph(attributePaths = {"user", "resumeDocument"})
    @Query("select c from Candidate c where lower(c.email) = lower(:email)")
    Optional<Candidate> findByEmailIgnoringSoftDelete(@Param("email") String email);

    @EntityGraph(attributePaths = {"user", "preferredCategory", "resumeDocument"})
    @Query("select c from Candidate c where c.user.id = :userId and c.deleted = false")
    Optional<Candidate> findActiveByUserId(@Param("userId") Long userId);

    @Query("select count(c) > 0 from Candidate c where c.user.id = :userId")
    boolean existsByUserId(@Param("userId") Long userId);
}
