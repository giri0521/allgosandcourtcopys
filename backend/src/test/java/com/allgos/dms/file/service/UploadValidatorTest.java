package com.allgos.dms.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

/**
 * What is allowed into the document store.
 *
 * <p>The point of these is that the <em>declared</em> content type is never what decides. Each test
 * sends bytes whose real signature disagrees with the name or the header, which is the shape of
 * every disguised-upload attempt.
 */
class UploadValidatorTest {

    /** A minimal but genuine PDF: the header, one object, and the trailer Tika looks for. */
    private static final byte[] REAL_PDF =
            "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF"
                    .getBytes(StandardCharsets.US_ASCII);

    /** The first bytes of a Windows executable. */
    private static final byte[] WINDOWS_EXECUTABLE = new byte[] {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00};

    private static final byte[] PNG =
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    private final UploadValidator validator = new UploadValidator(properties());

    @Test
    @DisplayName("a real PDF is accepted, and the detected type is what gets stored")
    void acceptsARealPdf() {
        var accepted = validator.validate(multipart("Government Order 12.pdf", "application/pdf", REAL_PDF));

        assertThat(accepted.fileName()).isEqualTo("Government Order 12.pdf");
        assertThat(accepted.contentType()).isEqualTo("application/pdf");
        assertThat(accepted.sizeBytes()).isEqualTo(REAL_PDF.length);
    }

    @Test
    @DisplayName("an executable renamed to .pdf is refused, however it declares itself")
    void refusesAnExecutableWearingAPdfName() {
        // This is the case the specification calls out: extension and header both say PDF, the
        // bytes say MZ. Only reading the signature catches it.
        assertThatThrownBy(() ->
                        validator.validate(multipart("invoice.pdf", "application/pdf", WINDOWS_EXECUTABLE)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FILE_CONTENT_MISMATCH");
    }

    @Test
    @DisplayName("a PNG renamed to .pdf is refused too — the mismatch is what matters, not malice")
    void refusesAnyMismatch() {
        assertThatThrownBy(() -> validator.validate(multipart("scan.pdf", "application/pdf", PNG)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FILE_CONTENT_MISMATCH");
    }

    @ParameterizedTest
    @ValueSource(strings = {"payload.exe", "script.sh", "archive.zip", "page.html", "notes"})
    @DisplayName("extensions outside the allow-list never reach the signature check")
    void refusesDisallowedExtensions(String filename) {
        assertThatThrownBy(() -> validator.validate(multipart(filename, "application/pdf", REAL_PDF)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("an empty file is refused")
    void refusesAnEmptyFile() {
        assertThatThrownBy(() -> validator.validate(multipart("empty.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FILE_EMPTY");
    }

    @Test
    @DisplayName("a filename carrying a path is reduced to its last segment")
    void stripsPathsFromFilenames() {
        assertThat(UploadValidator.safeName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(UploadValidator.safeName("C:\\Users\\clerk\\order.pdf")).isEqualTo("order.pdf");
        assertThat(UploadValidator.safeName("  ")).isEqualTo("document");
        assertThat(UploadValidator.safeName(null)).isEqualTo("document");
    }

    @Test
    @DisplayName("a traversal filename is accepted only after being made safe")
    void acceptsATraversalNameOnlyAsItsSafeForm() {
        var accepted = validator.validate(multipart("../../secret/order.pdf", "application/pdf", REAL_PDF));

        assertThat(accepted.fileName()).isEqualTo("order.pdf");
    }

    private static MockMultipartFile multipart(String filename, String declaredType, byte[] content) {
        return new MockMultipartFile("files", filename, declaredType, content);
    }

    private static AppProperties properties() {
        return new AppProperties(
                "Asia/Kolkata",
                null,
                null,
                null,
                new AppProperties.Upload(List.of(
                        "application/pdf",
                        "image/jpeg",
                        "image/png",
                        "image/tiff",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
                null);
    }
}
