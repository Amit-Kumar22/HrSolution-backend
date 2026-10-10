package com.hrsolution.client.repository;

import com.hrsolution.client.entity.ClientUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientUserRepository extends JpaRepository<ClientUser, Long> {

    /**
     * Resolves a login to its client. The single source of truth for every
     * ownership check, which is why it is keyed on the user rather than taking
     * a client id from the caller.
     *
     * <p>Only active links count: deactivating a person's access must take
     * effect without deleting the row that explains what they used to see.
     */
    @EntityGraph(attributePaths = {"client"})
    @Query("""
            select cu from ClientUser cu
            where cu.user.id = :userId and cu.active = true and cu.client.deleted = false
            """)
    Optional<ClientUser> findActiveByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            select cu from ClientUser cu
            where cu.client.id = :clientId
            order by cu.primaryContact desc, cu.id asc
            """)
    List<ClientUser> findByClientId(@Param("clientId") Long clientId);

    @EntityGraph(attributePaths = {"user", "client"})
    @Query("select cu from ClientUser cu where cu.id = :id")
    Optional<ClientUser> findByIdWithUserAndClient(@Param("id") Long id);

    boolean existsByUserId(Long userId);

    /**
     * Clears the primary-contact flag across a client before setting a new one.
     *
     * <p>A single UPDATE because "at most one primary contact" cannot be
     * expressed as a MySQL unique constraint - it would need a partial index.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ClientUser cu set cu.primaryContact = false
            where cu.client.id = :clientId and cu.primaryContact = true
            """)
    int clearPrimaryContactFor(@Param("clientId") Long clientId);
}
