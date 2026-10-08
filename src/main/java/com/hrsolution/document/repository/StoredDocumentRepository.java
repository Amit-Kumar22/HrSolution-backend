package com.hrsolution.document.repository;

import com.hrsolution.document.entity.DocumentOwnerType;
import com.hrsolution.document.entity.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, Long> {

    @Query("select d from StoredDocument d where d.id = :id and d.deleted = false")
    Optional<StoredDocument> findActiveById(@Param("id") Long id);

    @Query("""
            select d from StoredDocument d
            where d.storageKey = :storageKey and d.deleted = false
            """)
    Optional<StoredDocument> findActiveByStorageKey(@Param("storageKey") String storageKey);

    @Query("""
            select d from StoredDocument d
            where d.ownerType = :ownerType
              and (:ownerId is null or d.ownerId = :ownerId)
              and d.deleted = false
            order by d.createdAt desc
            """)
    List<StoredDocument> findActiveByOwner(@Param("ownerType") DocumentOwnerType ownerType,
                                           @Param("ownerId") Long ownerId);

    /**
     * Finds an existing upload of identical content for the same owner.
     *
     * <p>Used to avoid storing the same scan twice when a user double-submits a
     * form - common on a slow connection, where the first response never
     * arrived.
     */
    @Query("""
            select d from StoredDocument d
            where d.checksumSha256 = :checksum
              and d.ownerType = :ownerType
              and d.deleted = false
            """)
    List<StoredDocument> findActiveByChecksumAndOwnerType(@Param("checksum") String checksum,
                                                          @Param("ownerType") DocumentOwnerType ownerType);
}
