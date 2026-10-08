package com.hrsolution.common.storage;

import com.hrsolution.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Stores files on the local filesystem under {@code app.storage.local-path}.
 *
 * <h2>Layout</h2>
 * <pre>{@code uploads/<category>/<yyyy>/<MM>/<uuid><ext>}</pre>
 *
 * <p>Dated subdirectories keep any single directory to a manageable number of
 * entries - a flat directory with hundreds of thousands of worker documents is
 * slow to list and awkward to back up incrementally.
 *
 * <p><strong>The file name is never used to build the path.</strong> It is
 * replaced by a UUID, keeping only a sanitised extension. This removes an
 * entire class of problem at the root: a name like
 * {@code ../../../../etc/cron.d/x} cannot escape the storage root if it is
 * never part of the path. The original name is kept in the database for
 * display. A UUID also means two users uploading {@code aadhaar.pdf} cannot
 * overwrite one another.
 *
 * <p>Every resolved path is additionally checked to be inside the storage root
 * before any read or write - belt and braces, because a traversal bug here
 * would be read-anything / write-anything on the server.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final AppProperties appProperties;

    private Path storageRoot() {
        return Path.of(appProperties.getStorage().getLocalPath()).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(StoreRequest request) {
        byte[] content = readFully(request.content());

        if (content.length == 0) {
            throw StorageException.rejected("The uploaded file is empty.");
        }

        String storageKey = buildStorageKey(request.category(), request.fileName());
        Path target = resolveWithinRoot(storageKey);

        try {
            Files.createDirectories(target.getParent());
            // Write to a temporary file and move it into place, so a crash
            // mid-write cannot leave a truncated file that looks complete.
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".part");
            try {
                Files.write(temporary, content);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(temporary);
                throw e;
            }
        } catch (IOException e) {
            // Path goes to the log, not to the caller.
            log.error("Failed to write upload to {}", target, e);
            throw StorageException.failed("Could not store the uploaded file.", e);
        }

        log.debug("Stored {} bytes at {}", content.length, storageKey);

        return new StoredFile(
                storageKey,
                request.fileName(),
                request.contentType(),
                content.length,
                sha256Hex(content));
    }

    @Override
    public InputStream load(String storageKey) {
        Path source = resolveWithinRoot(storageKey);
        if (!Files.exists(source)) {
            throw StorageException.notFound(storageKey);
        }
        try {
            return Files.newInputStream(source);
        } catch (IOException e) {
            log.error("Failed to read stored file {}", source, e);
            throw StorageException.failed("Could not read the stored file.", e);
        }
    }

    @Override
    public boolean delete(String storageKey) {
        try {
            return Files.deleteIfExists(resolveWithinRoot(storageKey));
        } catch (IOException e) {
            log.error("Failed to delete stored file {}", storageKey, e);
            throw StorageException.failed("Could not delete the stored file.", e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolveWithinRoot(storageKey));
    }

    // ------------------------------------------------------------------

    /**
     * Builds {@code category/yyyy/MM/uuid.ext}.
     *
     * <p>The category is a server-chosen constant, and the only thing taken
     * from the upload is an extension validated against a strict pattern.
     */
    private String buildStorageKey(String category, String fileName) {
        LocalDate today = LocalDate.now();
        return "%s/%d/%02d/%s%s".formatted(
                sanitiseCategory(category),
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                safeExtension(fileName));
    }

    /** Categories are internal constants, but normalised anyway. */
    private String sanitiseCategory(String category) {
        if (category == null || category.isBlank()) {
            return "misc";
        }
        String cleaned = category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]", "");
        return cleaned.isEmpty() ? "misc" : cleaned;
    }

    /**
     * Extracts a lower-case extension of plain letters and digits, or nothing.
     *
     * <p>Deliberately strict. Anything unusual is dropped rather than
     * sanitised: a file with no extension is harmless, whereas a cleverly
     * encoded one is the start of a traversal or a double-extension trick.
     */
    private String safeExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("[a-z0-9]{1,10}")) {
            return "";
        }
        return "." + extension;
    }

    /**
     * Resolves a key against the storage root and refuses to leave it.
     *
     * <p>Keys are server-generated, so this should be unreachable. It is here
     * because the cost of being wrong is arbitrary file read or write, and the
     * check is three lines.
     */
    private Path resolveWithinRoot(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw StorageException.rejected("No storage key was supplied.");
        }
        Path root = storageRoot();
        Path resolved = root.resolve(storageKey).normalize();

        if (!resolved.startsWith(root)) {
            log.error("Blocked a path traversal attempt: key '{}' resolved to {}, outside {}",
                    storageKey, resolved, root);
            throw StorageException.rejected("Invalid storage key.");
        }
        return resolved;
    }

    private byte[] readFully(InputStream content) {
        try (InputStream stream = content) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw StorageException.failed("Could not read the uploaded file.", e);
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", e);
        }
    }
}
