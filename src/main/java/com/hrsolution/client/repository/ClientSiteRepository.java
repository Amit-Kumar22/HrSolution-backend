package com.hrsolution.client.repository;

import com.hrsolution.client.entity.ClientSite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientSiteRepository extends JpaRepository<ClientSite, Long>,
        JpaSpecificationExecutor<ClientSite> {

    @EntityGraph(attributePaths = {"client"})
    @Query("select s from ClientSite s where s.id = :id and s.deleted = false")
    Optional<ClientSite> findActiveById(@Param("id") Long id);

    @Query("""
            select s from ClientSite s
            where s.client.id = :clientId and s.deleted = false
            order by s.active desc, s.siteName asc
            """)
    List<ClientSite> findByClientId(@Param("clientId") Long clientId);

    @Query("""
            select count(s) > 0 from ClientSite s
            where s.client.id = :clientId and upper(s.siteCode) = upper(:siteCode)
              and s.deleted = false
            """)
    boolean existsByClientAndSiteCode(@Param("clientId") Long clientId,
                                      @Param("siteCode") String siteCode);

    long countByClientIdAndActiveTrueAndDeletedFalse(Long clientId);
}
