package com.hrsolution.document.service;

import com.hrsolution.common.config.AppProperties;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.SecurityUtils;
import com.hrsolution.common.storage.FileTypeDetector;
import com.hrsolution.common.storage.StorageException;
import com.hrsolution.common.storage.StorageService;
import com.hrsolution.common.storage.StoredFile;
import com.hrsolution.common.storage.VirusScanner;
import com.hrsolution.document.entity.DocumentOwnerType;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.document.repository.StoredDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Validates, stores and retrieves uploaded files.
 *
 * <p>The validation order matters and is not arbitrary:
 *
 * <ol>
 *   <li><strong>Size</strong> — cheapest check, and rejecting early avoids
 *       reading a huge body into memory.</li>
 *   <li><strong>Content type, from the bytes</strong> — never from the file name
 *       or the {@code Content-Type} header, both of which the client controls.
 *       See {@link FileTypeDetector} for why that distinction is a security
 *       boundary rather than a nicety.</li>
 *   <li><strong>Malware scan</strong> — a no-op by default; see
 *       {@link VirusScanner}.</li>
 *   <li><strong>Store</strong>, then record the metadata row.</li>
 * </ol>
 *
 * <p>Nothing is written to disk until every check has passed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final StorageService storageService;
    private final StoredDocumentRepository documentRepository;
    private final VirusScanner virusScanner;
    private final AppProperties appProperties;

    /** A document plus an open stream of its content, for download endpoints. */
    public record DownloadableDocument(StoredDocument metadata, InputStream content) {
    }

    /**
     * Stores an upload against an owner.
     *
     * @param allowedTypes which content types are acceptable here. Pass the
     *                     narrowest list that works - {@code IMAGES} for a logo,
     *                     not {@code IMAGES_AND_PDF}.
     */
    @Transactional
    public StoredDocument upload(MultipartFile file,
                                 DocumentOwnerType ownerType,
                                 Long ownerId,
                                 List<FileTypeDetector> allowedTypes) {
        if (file == null || file.isEmpty()) {
            throw StorageException.rejected("No file was uploaded.");
        }

        byte[] content = readBytes(file);
        long maxSize = maxSizeFor(allowedTypes);

        if (content.length > maxSize) {
            throw StorageException.rejected(
                    "The file is %.1f MB; the maximum is %.1f MB."
                            .formatted(content.length / 1_048_576.0, maxSize / 1_048_576.0));
        }

        FileTypeDetector detected = detectAndValidate(content, allowedTypes, file);
        virusScanner.scan(content, file.getOriginalFilename());

        StoredFile stored = storageService.store(new StorageService.StoreRequest(
                ownerType.getStorageCategory(),
                safeDisplayName(file.getOriginalFilename(), detected),
                // The DETECTED type is persisted, not the declared one. This is
                // what the download endpoint later sends as Content-Type, so a
                // client cannot talk the server into serving its upload as
                // text/html and getting it rendered on this origin.
                detected.getContentType(),
                content.length,
                new ByteArrayInputStream(content)));

        StoredDocument document = new StoredDocument();
        document.setOwnerType(ownerType);
        document.setOwnerId(ownerId);
        document.setOriginalFileName(stored.originalFileName());
        document.setStorageKey(stored.storageKey());
        document.setContentType(stored.contentType());
        document.setSizeBytes(stored.sizeBytes());
        document.setChecksumSha256(stored.checksumSha256());
        // Copied from the owner type at upload time, so reclassifying a type
        // later cannot retroactively expose files already stored as private.
        document.setPublicAsset(ownerType.isPublicAsset());
        document.setUploadedByUserId(SecurityUtils.currentUserId().orElse(null));

        StoredDocument saved = documentRepository.save(document);

        log.info("Stored document {} ({} bytes, {}) for {}/{}",
                saved.getId(), saved.getSizeBytes(), saved.getContentType(),
                ownerType, ownerId);

        return saved;
    }

    @Transactional(readOnly = true)
    public StoredDocument get(Long documentId) {
        return documentRepository.findActiveById(documentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Document", documentId));
    }

    @Transactional(readOnly = true)
    public List<StoredDocument> listForOwner(DocumentOwnerType ownerType, Long ownerId) {
        return documentRepository.findActiveByOwner(ownerType, ownerId);
    }

    /** Opens a document for download. The caller must close the stream. */
    @Transactional(readOnly = true)
    public DownloadableDocument download(Long documentId) {
        StoredDocument document = get(documentId);
        return new DownloadableDocument(document, storageService.load(document.getStorageKey()));
    }

    /**
     * Opens a public asset by storage key, refusing anything private.
     *
     * <p>Serves the marketing site, which has no credentials. The
     * {@code publicAsset} check is the whole security boundary here: without it,
     * knowing or guessing a key would expose every worker's identity documents.
     * A private key is reported as "not found" rather than "forbidden", so the
     * endpoint cannot be used to confirm that a document exists.
     */
    @Transactional(readOnly = true)
    public DownloadableDocument downloadPublicAsset(String storageKey) {
        StoredDocument document = documentRepository.findActiveByStorageKey(storageKey)
                .filter(StoredDocument::isPublicAsset)
                .orElseThrow(() -> {
                    log.warn("Anonymous request for a non-public or unknown storage key: {}",
                            storageKey);
                    return new ResourceNotFoundException("No public file exists at that address.");
                });

        return new DownloadableDocument(document, storageService.load(document.getStorageKey()));
    }

    /**
     * Soft-deletes the metadata row, leaving the bytes in place.
     *
     * <p>Deliberate: an accidental deletion stays recoverable, and an audit
     * entry that references the document does not end up pointing at nothing.
     * Purging the bytes of long-deleted rows is a separate housekeeping concern.
     */
    @Transactional
    public void delete(Long documentId) {
        StoredDocument document = get(documentId);
        document.markDeleted(SecurityUtils.currentUserEmail().orElse("system"));
        log.info("Soft-deleted document {} (file retained at {})",
                documentId, document.getStorageKey());
    }

    // ------------------------------------------------------------------

    /**
     * Identifies the content and checks it against the allowlist.
     *
     * <p>Also compares the result against the declared extension, purely to log
     * the mismatch. A {@code .pdf} that is really a JPEG is accepted when JPEG
     * is allowed - the content is what matters - but it is worth a line in the
     * log, since the usual cause is someone probing the upload validation.
     */
    private FileTypeDetector detectAndValidate(byte[] content,
                                               List<FileTypeDetector> allowedTypes,
                                               MultipartFile file) {
        byte[] header = Arrays.copyOf(content, Math.min(FileTypeDetector.SNIFF_LENGTH, content.length));

        FileTypeDetector detected = FileTypeDetector.detect(header)
                .orElseThrow(() -> {
                    log.warn("Rejected upload '{}' declaring {}: content matches no known type",
                            file.getOriginalFilename(), file.getContentType());
                    return StorageException.rejected(
                            "That file type is not supported. Allowed: " + describe(allowedTypes));
                });

        if (!allowedTypes.contains(detected)) {
            log.warn("Rejected upload '{}': content is {} but this endpoint allows {}",
                    file.getOriginalFilename(), detected.getContentType(), describe(allowedTypes));
            throw StorageException.rejected(
                    "A %s file is not accepted here. Allowed: %s"
                            .formatted(detected.getContentType(), describe(allowedTypes)));
        }

        String declaredName = file.getOriginalFilename();
        if (declaredName != null && !declaredName.toLowerCase()
                .endsWith(detected.getDefaultExtension())) {
            log.info("Upload '{}' is actually {} - stored by detected type, not by name",
                    declaredName, detected.getContentType());
        }

        return detected;
    }

    /** Images get the tighter cap; anything that may be a PDF gets the general one. */
    private long maxSizeFor(List<FileTypeDetector> allowedTypes) {
        AppProperties.Storage storage = appProperties.getStorage();
        boolean imagesOnly = allowedTypes.stream()
                .allMatch(FileTypeDetector.IMAGES::contains);
        return imagesOnly ? storage.getMaxImageSizeBytes() : storage.getMaxFileSizeBytes();
    }

    private String describe(List<FileTypeDetector> allowedTypes) {
        return allowedTypes.stream().map(FileTypeDetector::getContentType).sorted().toList()
                .toString();
    }

    /**
     * A display name safe to echo back in JSON and in a
     * {@code Content-Disposition} header.
     *
     * <p>Strips path separators, control characters and quotes. Quotes matter
     * specifically: an unescaped one in a filename lets a crafted upload break
     * out of the {@code filename="..."} parameter and inject header content.
     */
    private String safeDisplayName(String originalName, FileTypeDetector detected) {
        if (originalName == null || originalName.isBlank()) {
            return "upload" + detected.getDefaultExtension();
        }
        String cleaned = originalName
                .replaceAll("[\\\\/\\r\\n\"]", "")
                .replaceAll("\\p{Cntrl}", "")
                .trim();
        if (cleaned.isEmpty()) {
            return "upload" + detected.getDefaultExtension();
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw StorageException.failed("Could not read the uploaded file.", e);
        }
    }
}
