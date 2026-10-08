package com.hrsolution.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content-based file type detection.
 *
 * <p>The decisive test here is {@link #htmlDisguisedAsAnImageIsRejected()}. That
 * attack - upload HTML carrying script as {@code logo.png}, have the server
 * serve it back, and get it rendered on this application's origin - is stored
 * XSS, and validating the extension or the declared {@code Content-Type} would
 * not stop it.
 */
class FileTypeDetectorTest {

    /** 8-byte PNG signature. */
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    private static final byte[] GIF_HEADER = {'G', 'I', 'F', '8', '9', 'a'};

    private static final byte[] PDF_HEADER = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};

    private static final byte[] ZIP_HEADER = {'P', 'K', 0x03, 0x04};

    @Test
    @DisplayName("identifies PNG, JPEG, GIF, PDF and ZIP from their signatures")
    void identifiesKnownTypes() {
        assertThat(FileTypeDetector.detect(PNG_HEADER)).contains(FileTypeDetector.PNG);
        assertThat(FileTypeDetector.detect(JPEG_HEADER)).contains(FileTypeDetector.JPEG);
        assertThat(FileTypeDetector.detect(GIF_HEADER)).contains(FileTypeDetector.GIF);
        assertThat(FileTypeDetector.detect(PDF_HEADER)).contains(FileTypeDetector.PDF);
        assertThat(FileTypeDetector.detect(ZIP_HEADER)).contains(FileTypeDetector.ZIP_CONTAINER);
    }

    @Test
    @DisplayName("HTML renamed to .png is not detected as an image")
    void htmlDisguisedAsAnImageIsRejected() {
        byte[] html = "<html><script>fetch('/api/v1/users')</script></html>"
                .getBytes(StandardCharsets.UTF_8);

        // Nothing about the name or the declared Content-Type is consulted - the
        // bytes simply do not match an image signature, so the upload is
        // refused. This is the difference between type validation that works and
        // type validation that only looks like it does.
        assertThat(FileTypeDetector.detect(html)).isEmpty();
    }

    @Test
    @DisplayName("an SVG is not accepted as an image")
    void svgIsNotAnAllowedImage() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        // SVG is deliberately absent from the enum. It is XML, it can carry
        // script, and browsers execute that script when the file is served
        // inline - so it is excluded rather than sanitised.
        assertThat(FileTypeDetector.detect(svg)).isEmpty();
    }

    @Test
    @DisplayName("handles the high-bit PNG signature byte correctly")
    void handlesSignedBytes() {
        // 0x89 is -119 as a signed Java byte. Comparing without masking to
        // 0xFF would never match, so PNG uploads would be rejected outright.
        assertThat(PNG_HEADER[0]).isNegative();
        assertThat(FileTypeDetector.detect(PNG_HEADER)).contains(FileTypeDetector.PNG);
    }

    @Test
    @DisplayName("rejects content shorter than the signature it would match")
    void rejectsTruncatedContent() {
        assertThat(FileTypeDetector.detect(new byte[]{(byte) 0x89, 'P'})).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[0])).isEmpty();
        assertThat(FileTypeDetector.detect(null)).isEmpty();
    }

    @Test
    @DisplayName("the image allowlist excludes PDF, and the document one includes it")
    void allowlistsAreScopedAsIntended() {
        assertThat(FileTypeDetector.IMAGES)
                .containsExactlyInAnyOrder(
                        FileTypeDetector.PNG, FileTypeDetector.JPEG, FileTypeDetector.GIF)
                .doesNotContain(FileTypeDetector.PDF);

        assertThat(FileTypeDetector.IMAGES_AND_PDF).contains(FileTypeDetector.PDF);
    }

    @Test
    @DisplayName("every type reports a content type and an extension")
    void everyTypeIsFullyDescribed() {
        for (FileTypeDetector type : FileTypeDetector.values()) {
            assertThat(type.getContentType()).as("%s content type", type).isNotBlank();
            assertThat(type.getDefaultExtension()).as("%s extension", type).startsWith(".");
            assertThat(type.getMagicBytes()).as("%s magic bytes", type).isNotEmpty();
        }
    }
}
