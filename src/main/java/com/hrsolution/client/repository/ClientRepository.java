package com.hrsolution.client.repository;

import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long>,
        JpaSpecificationExecutor<Client> {

    @EntityGraph(attributePaths = {"industry"})
    @Query("select c from Client c where c.id = :id and c.deleted = false")
    Optional<Client> findActiveById(@Param("id") Long id);

    /**
     * Existence check for a GSTIN. Includes soft-deleted rows, because the
     * unique index covers them too - ignoring them would turn a clear 409 into
     * a database integrity error.
     */
    @Query("select count(c) > 0 from Client c where c.gstin = :gstin")
    boolean existsByGstinIgnoringSoftDelete(@Param("gstin") String gstin);

    @Query("""
            select count(c) > 0 from Client c
            where lower(c.legalName) = lower(:legalName) and c.deleted = false
            """)
    boolean existsByLegalNameIgnoreCase(@Param("legalName") String legalName);

    long countByStatusAndDeletedFalse(ClientStatus status);
}
