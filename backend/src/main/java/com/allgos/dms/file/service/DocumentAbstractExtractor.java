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
 * Pulls the "Abstract" paragraph, the department line right below it, and the G.O. number out of a
 * government order PDF — for the description shown beside the document, for guessing where an
 * unfiled upload belongs, and so the document can be found again by that number later.
 *
 * <p>These orders follow a layout closely enough to rely on: the first page opens with a heading
 * reading "ABSTRACT", a paragraph that always ends with the word "Issued.", and then, on its own
 * line, the department the order belongs to (e.g. "Public Works [Estt-I(1)] Department") before the
 * G.O. number. Those anchors are what is searched for, rather than anything more elaborate. The G.O.
 * number itself is looked for across the whole page rather than anchored to the Abstract, since it
 * normally sits in the letterhead above it, not inside it.
 *
 * <p>Plenty of what this office uploads as a "PDF" turns out to carry no text layer at all — a scan
 * saved straight to PDF — so whenever the first page yields next to no native text, it is rendered
 * to an image and read with Tesseract OCR instead. Either path, on either kind of document, is a
 * best-effort enrichment: a failure here is logged and produces an empty {@link Extraction}, never a
 * failed upload.
 */
@Component
public class DocumentAbstractExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentAbstractExtractor.class);

    /** Below this many characters of native text, page 1 is treated as a scanned image. */
    private static final int NATIVE_TEXT_THRESHOLD = 80;

    private static final int OCR_DPI = 300;

    /** A sanity bound on what gets stored — never reached by the paragraphs this looks for. */
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    /** Group 1 is the abstract paragraph; group 2 is the department line straight after it. */
    private static final Pattern ABSTRACT_PATTERN = Pattern.compile(
            "ABSTRACT[^\\n]*\\n(.*?Issued\\.)\\s*\\n+([^\\n]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * The G.O. reference line, wherever it sits on the page — usually in the letterhead above the
     * Abstract, not inside it. Tamil Nadu government orders write this several ways: "G.O.Ms.No.123",
     * "G.O. (2D) No. 45", "G.O. Rt. No. 456" and plain "Government Order No. 123" all show up, so the
     * type marker in the parentheses/abbreviation is optional and the whole match — not just the
     * digits — is what gets stored, since "Ms.No.123" and "Rt.No.123" are different orders.
     */
    private static final Pattern GO_NUMBER_PATTERN = Pattern.compile(
            "(?:G(?:overnment)?\\.?\\s*O(?:rder)?\\.?\\s*(?:\\([^)\\n]{1,10}\\)\\s*)?"
                    + "(?:M\\.?S\\.?|Ms\\.?|Rt\\.?|D\\.?)?\\s*No\\.?\\s*[:.\\-]?\\s*(\\d[\\d/\\-]*))",
            Pattern.CASE_INSENSITIVE);

    /**
     * Where the "Read:" block starts — everything from here on is a list of <em>earlier</em> G.O.s
     * this order amends or refers back to, e.g. "G.O.(Ms) No.111, Finance (TAPS) Department, dated
     * 16.06.2026". A document can cite any number of these, and none of them is the number of the
     * document itself, so the search for the real G.O. number stops here rather than picking
     * whichever one {@link Matcher#find()} happens to reach first.
     */
    private static final Pattern READ_MARKER = Pattern.compile("read\\s*:", Pattern.CASE_INSENSITIVE);

    /** A sanity bound on what gets stored for the G.O. number, mirroring the description's. */
    private static final int MAX_GO_NUMBER_LENGTH = 100;

    /** Null when the bundled language data could not be staged; OCR is then skipped rather than failing. */
    private final Path tessdataDir = stageTessdata();

    /**
     * What was read from the document's own text. Any field may be null on its own — a document can
     * carry a G.O. number with no Abstract heading, or the reverse.
     */
    public record Extraction(String description, String departmentHint, String goNumber) {
        public static final Extraction NONE = new Extraction(null, null, null);
    }

    /**
     * @return the Abstract paragraph and department line, or {@link Extraction#NONE} when the bytes
     *     are not a readable PDF or do not follow the convention this looks for
     */
    public Extraction extract(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                return Extraction.NONE;
            }

            String text = nativeText(document);
            if (text.trim().length() < NATIVE_TEXT_THRESHOLD) {
                text = ocrFirstPage(document);
            }
            Extraction fromAbstract = findAbstract(text);
            return new Extraction(fromAbstract.description(), fromAbstract.departmentHint(), findGoNumber(text));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not extract a description from an uploaded PDF", ex);
            return Extraction.NONE;
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

    private static Extraction findAbstract(String text) {
        Matcher matcher = ABSTRACT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Extraction.NONE;
        }

        String description = collapse(matcher.group(1));
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        }

        String departmentHint = collapse(matcher.group(2));
        return new Extraction(description, departmentHint, null);
    }

    /**
     * @return the document's own G.O. number, or null when there is none.
     *     <p>Searched for only <em>before</em> the "Read:" block, when there is one — a G.O. can cite
     *     any number of earlier orders there, and taking the first match anywhere on the page would
     *     just as happily return one of those instead of the number of the document itself.
     */
    private static String findGoNumber(String text) {
        Matcher readMatcher = READ_MARKER.matcher(text);
        String heading = readMatcher.find() ? text.substring(0, readMatcher.start()) : text;

        String goNumber = firstGoNumberIn(heading);
        // Text extraction does not always preserve visual reading order for a multi-column
        // letterhead, so the heading's own number can occasionally land after "Read:" in the
        // extracted text even though it prints above it on the page — search the whole page rather
        // than reporting nothing.
        return goNumber != null || heading.length() == text.length() ? goNumber : firstGoNumberIn(text);
    }

    private static String firstGoNumberIn(String text) {
        Matcher matcher = GO_NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String goNumber = collapse(matcher.group());
        if (goNumber != null && goNumber.length() > MAX_GO_NUMBER_LENGTH) {
            goNumber = goNumber.substring(0, MAX_GO_NUMBER_LENGTH);
        }
        return goNumber;
    }

    private static String collapse(String raw) {
        String collapsed = raw.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
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
