package com.allgos.dms.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The export exists to be opened in Excel on a clerk's machine, and every assertion here is about
 * something Excel does that a plain string join would get wrong.
 */
class CsvWriterTest {

    @Test
    @DisplayName("the file starts with a UTF-8 BOM, or Excel mangles Tamil department names")
    void writesAByteOrderMark() throws Exception {
        byte[] output = write(List.of("Department"), List.of(List.of("வேளாண்மைத் துறை")));

        assertThat(output[0]).isEqualTo((byte) 0xEF);
        assertThat(output[1]).isEqualTo((byte) 0xBB);
        assertThat(output[2]).isEqualTo((byte) 0xBF);

        // And the name survives the round trip intact, which is the point of the BOM.
        assertThat(new String(output, StandardCharsets.UTF_8)).contains("வேளாண்மைத் துறை");
    }

    @Test
    @DisplayName("every field is quoted, so a comma in a name cannot shift a column")
    void quotesEveryField() throws Exception {
        String output = text(
                List.of("Department", "Uploads"),
                List.of(List.of("Backward Classes, Most Backward Classes and Minorities Welfare", "12")));

        assertThat(output)
                .contains("\"Backward Classes, Most Backward Classes and Minorities Welfare\",\"12\"");
    }

    @Test
    @DisplayName("a quote inside a field is doubled, the way Excel writes it itself")
    void escapesEmbeddedQuotes() throws Exception {
        String output = text(List.of("Document"), List.of(List.of("Circular \"42\" of 2026")));

        assertThat(output).contains("\"Circular \"\"42\"\" of 2026\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+A1", "-2", "@SUM(A1)"})
    @DisplayName("a value Excel would execute as a formula is neutralised")
    void prefixesFormulaCharacters(String dangerous) throws Exception {
        String output = text(List.of("Name"), List.of(List.of(dangerous)));

        // The apostrophe tells Excel to treat what follows as text. Filenames and member names
        // reach this export, so a document called "=cmd|..." must not run on a clerk's machine.
        assertThat(output).contains("\"'" + dangerous + "\"");
    }

    @Test
    @DisplayName("rows end with CRLF, which is what a .csv on Windows is expected to use")
    void usesWindowsLineEndings() throws Exception {
        String output = text(List.of("A", "B"), List.of(List.of("1", "2")));

        assertThat(output).isEqualTo("﻿\"A\",\"B\"\r\n\"1\",\"2\"\r\n");
    }

    @Test
    @DisplayName("a null cell is written as empty rather than the word null")
    void writesNullAsEmpty() throws Exception {
        String output = text(List.of("Department"), List.of(java.util.Arrays.asList((String) null)));

        assertThat(output).contains("\"\"").doesNotContain("null");
    }

    @Test
    void writesHeadersEvenWhenThereAreNoRows() throws Exception {
        String output = text(List.of("Month", "Uploads"), List.of());

        assertThat(output).isEqualTo("﻿\"Month\",\"Uploads\"\r\n");
    }

    private static byte[] write(List<String> headers, List<List<String>> rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvWriter.write(out, headers, rows);
        return out.toByteArray();
    }

    private static String text(List<String> headers, List<List<String>> rows) throws Exception {
        return new String(write(headers, rows), StandardCharsets.UTF_8);
    }
}
