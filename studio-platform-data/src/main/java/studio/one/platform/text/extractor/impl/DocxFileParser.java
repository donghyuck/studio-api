package studio.one.platform.text.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.IBodyElement;
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

            appendBodyElements(doc.getBodyElements(), sb);
            doc.getHeaderList().forEach(header -> appendBodyElements(header.getBodyElements(), sb));
            doc.getFooterList().forEach(footer -> appendBodyElements(footer.getBodyElements(), sb));

            return cleanText(sb.toString());

        } catch (IOException e) {
            throw new FileParseException("Failed to parse DOCX file: " + safeFilename(filename), e);
        }
    }

    private void appendBodyElements(List<IBodyElement> elements, StringBuilder sb) {
        for (IBodyElement element : elements) {
            switch (element.getElementType()) {
                case PARAGRAPH -> appendParagraph((XWPFParagraph) element, sb);
                case TABLE -> appendTable((XWPFTable) element, sb);
                default -> { /* ignore other elements */ }
            }
        }
    }

    private void appendParagraph(XWPFParagraph p, StringBuilder sb) {
        String text = p.getText();
        if (text != null && !text.isBlank()) {
            sb.append(text.trim()).append("\n");
        }
    }

    private void appendTable(XWPFTable table, StringBuilder sb) {
        for (XWPFTableRow row : table.getRows()) {
            String rowText = row.getTableCells().stream()
                    .map(XWPFTableCell::getText)
                    .collect(Collectors.joining(" | "));
            if (!rowText.isBlank()) {
                sb.append(rowText.trim()).append("\n");
            }
        }
    }
}
