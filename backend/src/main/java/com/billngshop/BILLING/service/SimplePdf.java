package com.billngshop.BILLING.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SimplePdf {
    private SimplePdf() {
    }

    public static byte[] render(String title, String text) {
        List<String> lines = new ArrayList<>();
        lines.add(title);
        for (String line : text.split("\\R")) {
            for (int start = 0; start < line.length(); start += 90) {
                lines.add(line.substring(start, Math.min(start + 90, line.length())));
            }
        }
        StringBuilder content = new StringBuilder("BT /F1 10 Tf 48 790 Td 12 TL\n");
        for (String line : lines.stream().limit(58).toList()) {
            content.append("(").append(escape(line)).append(") Tj T* \n");
        }
        content.append("ET");
        String stream = content.toString();
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n"
                        + stream + "\nendstream");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(output.size());
            write(output, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = output.size();
        write(output, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            write(output, String.format("%010d 00000 n \n", offset));
        }
        write(output, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF");
        return output.toByteArray();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static void write(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }
}
