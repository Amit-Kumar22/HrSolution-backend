package com.hrsolution.document.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Metadata for one stored file. The bytes live wherever
 * {@code StorageService} put them; this row is how they are found.
 *
 * <p>Named {@code StoredDocument} rather than {@code Document} to avoid
 * colliding with {@code org.w3c.dom.Document} and the several other
 * {@code Document} types that get imported in a project doing PDF generation -
 * a collision that produces genuinely baffling compile errors later.
 *
 * <p>Soft-deletable: a deleted row keeps pointing at its file so that an
 * accidental deletion can be undone, and so an audit trail referring to the
 * document does not dangle. A separate housekeeping job can purge the bytes of
 * long-deleted rows.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
public class StoredDocument extends SoftDeletableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 30)
    private DocumentOwnerType ownerType;

    /** Null for singleton owners such as the company logo. */
    @Column(name = "owner_id")
    private Long ownerId;

    /**
     * The name as uploaded. <strong>Display only.</strong> It is
     * attacker-controlled, and is never used to build a path - see
     * {@code LocalStorageService}.
     */
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /** Server-generated key; the only way to locate the bytes. */
    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    /** Detected from the file's content, not from its extension or header. */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    /**
     * Whether this file may be served anonymously. Copied from
     * {@link DocumentOwnerType#isPublicAsset()} at upload time rather than
     * derived on read, so that reclassifying an owner type later cannot
     * retroactively expose files already stored as private.
     */
    @Column(name = "public_asset", nullable = false)
    private boolean publicAsset = false;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    /** True when the content is an image, so a client can preview it inline. */
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
}
