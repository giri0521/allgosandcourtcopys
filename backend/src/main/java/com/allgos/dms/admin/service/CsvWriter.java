package com.allgos.dms.admin.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV, written for the program that will actually open it.
 *
 * <p>That program is Excel on a clerk's machine, and it makes two assumptions this class has to work
 * around:
 *
 * <ul>
 *   <li><b>Encoding.</b> Excel reads a .csv in the system codepage unless the file starts with a
 *       UTF-8 byte-order mark. Without it the Tamil department names arrive as mojibake — which is
 *       not a cosmetic problem, because the reader cannot tell a corrupted name from an unfamiliar
 *       one.
 *   <li><b>Formulas.</b> A cell beginning {@code =}, {@code +}, {@code -} or {@code @} is executed
 *       rather than displayed. Our data comes from user-supplied names, so those values are
 *       prefixed with an apostrophe: a spreadsheet that runs what a member typed into a filename is
 *       a real attack, not a theoretical one.
 * </ul>
 *
 * <p>Every field is quoted rather than only the ones that need it. The rule is then one line long
 * instead of a condition per character class, and no comma in a department name can shift a column.
 */
public final class CsvWriter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Excel treats a leading one of these as the start of a formula. */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    private final Writer writer;

    private CsvWriter(Writer writer) {
        this.writer = writer;
    }

    /**
     * Writes a whole sheet to the stream and flushes it.
     *
     * <p>Rows are supplied as a list rather than streamed lazily because every report here is
     * bounded — 43 departments, ten uploaders, twelve months. The audit log is the one export that
     * could grow without limit, and it is paged rather than exported whole for that reason.
     */
    public static void write(OutputStream out, List<String> headers, List<List<String>> rows)
            throws IOException {
        out.write(UTF8_BOM);

        CsvWriter csv = new CsvWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        csv.writeRow(headers);
        for (List<String> row : rows) {
            csv.writeRow(row);
        }
        csv.writer.flush();
    }

    private void writeRow(List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(quote(values.get(index)));
        }
        // CRLF: the line ending every spreadsheet on Windows expects from a .csv.
        writer.write("\r\n");
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value;

        if (!safe.isEmpty() && FORMULA_STARTERS.indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }

        // A quote inside a quoted field is escaped by doubling it — the CSV convention, and what
        // Excel writes itself.
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
