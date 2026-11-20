package com.java.dev.jim.personal.importer;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

final class CsvRows {
    private CsvRows() { }
    static List<Map<String, String>> read(Path file, Charset charset, String requiredHeader) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, charset)) {
            String line; List<String> headers = null; List<Map<String, String>> rows = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                List<String> values = parse(line);
                if (headers == null) {
                    if (values.stream().anyMatch(value -> ParserSupport.normalizeHeader(value).contains(requiredHeader))) {
                        headers = values.stream().map(ParserSupport::normalizeHeader).toList();
                    }
                    continue;
                }
                if (values.isEmpty() || values.stream().allMatch(String::isBlank)) continue;
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.size(); index++) row.put(headers.get(index), index < values.size() ? values.get(index).trim() : "");
                rows.add(row);
            }
            if (headers == null) throw new IllegalArgumentException("未找到账单表头：" + requiredHeader);
            return rows;
        }
    }
    private static List<String> parse(String line) {
        List<String> values = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') { value.append(current); index++; }
                else quoted = !quoted;
            } else if (current == ',' && !quoted) { values.add(value.toString()); value.setLength(0); }
            else value.append(current);
        }
        values.add(value.toString()); return values;
    }
}
