package com.hrsolution.document.dto;

import com.hrsolution.document.entity.DocumentOwnerType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Metadata for a stored file.
 *
 * <p>{@code storageKey} is exposed only for public assets, where it forms part
 * of the public URL. For private documents it is omitted: the key is the one
 * thing an unauthenticated caller would need, and there is no reason to hand it
 * out when {@code downloadUrl} already covers every legitimate use.
 */
@Schema(description = "An uploaded file")
public record DocumentResponse(
        Long id,
        DocumentOwnerType ownerType,
        Long ownerId,
        String originalFileName,
        @Schema(description = "Detected from the file's content, not its extension",
                example = "image/png")
        String contentType,
        long sizeBytes,
        boolean image,
        @Schema(description = "Whether this file can be fetched without authentication")
        boolean publicAsset,
        @Schema(description = "Where to fetch the bytes")
        String downloadUrl,
        Instant createdAt) {
}
