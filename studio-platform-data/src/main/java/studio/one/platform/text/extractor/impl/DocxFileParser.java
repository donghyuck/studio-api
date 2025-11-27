package studio.one.platform.text.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import studio.one.platform.text.extractor.FileParseException;

public class DocxFileParser extends AbstractFileParser {

    @Override
    public boolean supports(String contentType, String filename) {
        if (isContentType(contentType, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            return true;
        return hasExtension(filename, ".docx");
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                XWPFDocument doc = new XWPFDocument(in)) {

            StringBuilder sb = new StringBuilder();

            // 모든 단락
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }

            // 모든 테이블 텍스트
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    String rowText = row.getTableCells().stream()
                            .map(XWPFTableCell::getText)
                            .collect(Collectors.joining(" | "));
                    if (!rowText.isBlank()) {
                        sb.append(rowText).append("\n");
                    }
                }
            }

            return sb.toString();

        } catch (IOException e) {
            throw new FileParseException("Failed to parse DOCX file: " + safeFilename(filename), e);
        }
    }
}
