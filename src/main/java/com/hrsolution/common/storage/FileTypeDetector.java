package com.hrsolution.common.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Identifies a file from its leading bytes, not from its name.
 *
 * <p><strong>Why the extension cannot be trusted.</strong> Both the file name
 * and the {@code Content-Type} header come from the client, so a
 * {@code .pdf} upload declaring {@code application/pdf} may contain anything at
 * all. Validating either one is security theatre: an attacker uploads a
 * {@code .jpg} that is really an HTML file carrying script, the server serves it
 * back, and the browser renders it as HTML on this application's origin -
 * stored XSS, with the whole session exposed.
 *
 * <p>So the content decides. A file whose bytes do not match an allowed
 * signature is rejected, whatever it claims to be.
 *
 * <p>Signatures are checked on the first few bytes, which is sufficient to tell
 * these formats apart. This is not a general-purpose detector (Apache Tika
 * would be) - it covers exactly the handful of types this application accepts,
 * which is the point: a short allowlist is far easier to reason about than a
 * library that recognises a thousand formats.
 */
@Getter
@RequiredArgsConstructor
public enum FileTypeDetector {

    PNG("image/png", ".png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    JPEG("image/jpeg", ".jpg", new int[]{0xFF, 0xD8, 0xFF}),
    GIF("image/gif", ".gif", new int[]{0x47, 0x49, 0x46, 0x38}),
    PDF("application/pdf", ".pdf", new int[]{0x25, 0x50, 0x44, 0x46}),
    /**
     * ZIP container. Every modern Office format (.xlsx, .docx) is a ZIP, so
     * this signature alone cannot distinguish a spreadsheet from an arbitrary
     * archive - callers that accept it must restrict by extension as well.
     */
    ZIP_CONTAINER("application/zip", ".zip", new int[]{0x50, 0x4B, 0x03, 0x04}),
    /** Legacy OLE2 container: .doc and .xls. */
    OLE2("application/vnd.ms-excel", ".xls",
            new int[]{0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1});

    private final String contentType;
    private final String defaultExtension;
    private final int[] magicBytes;

    /** How many bytes {@link #detect} needs to look at. */
    public static final int SNIFF_LENGTH = 16;

    /** Image types, for logos, photos and scans. */
    public static final List<FileTypeDetector> IMAGES = List.of(PNG, JPEG, GIF);

    /** What a document upload may be: an image or a PDF. */
    public static final List<FileTypeDetector> IMAGES_AND_PDF = List.of(PNG, JPEG, GIF, PDF);

    /** Spreadsheets, for the bulk imports in Phases 6 and 7. */
    public static final List<FileTypeDetector> SPREADSHEETS = List.of(ZIP_CONTAINER, OLE2);

    /**
     * Identifies {@code header}, or empty when it matches nothing known.
     *
     * @param header the first bytes of the file, at least {@link #SNIFF_LENGTH}
     *               where available
     */
    public static Optional<FileTypeDetector> detect(byte[] header) {
        if (header == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(candidate -> candidate.matches(header))
                .findFirst();
    }

    private boolean matches(byte[] header) {
        if (header.length < magicBytes.length) {
            return false;
        }
        for (int i = 0; i < magicBytes.length; i++) {
            // & 0xFF because Java bytes are signed: 0x89 arrives as -119.
            if ((header[i] & 0xFF) != magicBytes[i]) {
                return false;
            }
        }
        return true;
    }
}
