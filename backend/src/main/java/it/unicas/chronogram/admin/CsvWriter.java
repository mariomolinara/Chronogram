package it.unicas.chronogram.admin;

import java.util.List;

/**
 * Minimal RFC 4180 CSV builder. Small enough not to justify a dependency, but
 * strict about the two things that silently corrupt exports: quoting anything
 * containing a delimiter, quote or newline, and rendering nulls as empty fields
 * rather than the string "null".
 */
final class CsvWriter {

    private static final char SEPARATOR = ',';
    private static final String LINE_END = "\r\n";

    private final StringBuilder out = new StringBuilder();

    CsvWriter(List<String> header) {
        writeRow(header.toArray());
    }

    void writeRow(Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(SEPARATOR);
            }
            out.append(escape(values[i]));
        }
        out.append(LINE_END);
    }

    String toCsv() {
        return out.toString();
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        boolean needsQuoting = text.indexOf(SEPARATOR) >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
