package com.allgos.dms.file.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pulls the "Abstract" paragraph out of a government order PDF, for the description shown beside
 * the document in the file list.
 *
 * <p>These orders follow one convention closely enough to rely on: the first page opens with a
 * heading reading "ABSTRACT", followed by a paragraph that always ends with the word "Issued." —
 * that pair of anchors is what is searched for, rather than anything more elaborate.
 *
 * <p>Plenty of what this office uploads as a "PDF" turns out to carry no text layer at all — a scan
 * saved straight to PDF — so whenever the first page yields next to no native text, it is rendered
 * to an image and read with Tesseract OCR instead. Either path, on either kind of document, is a
 * best-effort enrichment: a failure here is logged and produces no description, never a failed
 * upload.
 */
@Component
public class DocumentAbstractExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentAbstractExtractor.class);

    /** Below this many characters of native text, page 1 is treated as a scanned image. */
    private static final int NATIVE_TEXT_THRESHOLD = 80;

    private static final int OCR_DPI = 300;

    /** A sanity bound on what gets stored — never reached by the paragraphs this looks for. */
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private static final Pattern ABSTRACT_PATTERN =
            Pattern.compile("ABSTRACT[^\\n]*\\n(.*?Issued\\.)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Null when the bundled language data could not be staged; OCR is then skipped rather than failing. */
    private final Path tessdataDir = stageTessdata();

    /**
     * @return the Abstract paragraph, collapsed to one line, or {@code null} when the bytes are not
     *     a readable PDF or do not follow the convention this looks for
     */
    public String extract(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                return null;
            }

            String text = nativeText(document);
            if (text.trim().length() < NATIVE_TEXT_THRESHOLD) {
                text = ocrFirstPage(document);
            }
            return findAbstract(text);
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not extract a description from an uploaded PDF", ex);
            return null;
        }
    }

    private static String nativeText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(1);
        stripper.setEndPage(1);
        return stripper.getText(document);
    }

    private String ocrFirstPage(PDDocument document) throws IOException {
        if (tessdataDir == null) {
            return "";
        }

        BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, OCR_DPI);

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataDir.toString());
        tesseract.setLanguage("eng");
        try {
            return tesseract.doOCR(image);
        } catch (TesseractException ex) {
            throw new IOException("OCR failed", ex);
        }
    }

    private static String findAbstract(String text) {
        Matcher matcher = ABSTRACT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String collapsed = matcher.group(1).replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed.length() > MAX_DESCRIPTION_LENGTH
                ? collapsed.substring(0, MAX_DESCRIPTION_LENGTH)
                : collapsed;
    }

    /**
     * Tesseract reads its language model from a real file, not the classpath, so the bundled
     * resource is copied out once per process to a temp location that outlives this call.
     */
    private static Path stageTessdata() {
        try {
            Path dir = Files.createTempDirectory("allgos-tessdata");
            Path target = dir.resolve("eng.traineddata");
            try (InputStream in = DocumentAbstractExtractor.class.getResourceAsStream("/tessdata/eng.traineddata")) {
                if (in == null) {
                    throw new IOException("eng.traineddata is missing from the classpath");
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return dir;
        } catch (IOException ex) {
            log.error("Tesseract language data could not be staged; scanned PDFs will get no description", ex);
            return null;
        }
    }
}
