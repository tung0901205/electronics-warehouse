package com.tung.warehouse.common.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Tạo nội dung tệp CSV. Có ký tự BOM (\uFEFF) ở đầu để Excel mở đúng tiếng Việt có dấu.
 * Giá trị chứa dấu phẩy, dấu nháy kép hoặc xuống dòng được bọc trong dấu nháy kép (RFC 4180).
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static byte[] toBytes(List<String> headers, List<List<Object>> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(",", headers)).append("\r\n");
        for (List<Object> row : rows) {
            csv.append(String.join(",", row.stream().map(CsvWriter::escape).toList())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
