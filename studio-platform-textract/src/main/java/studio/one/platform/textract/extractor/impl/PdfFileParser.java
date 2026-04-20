package studio.one.platform.textract.extractor.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.textract.extractor.FileParseException;

@Slf4j
public class PdfFileParser extends AbstractFileParser {

    @Override
    public boolean supports(String contentType, String filename) {
        try {
            if (contentType != null) {
                MediaType mt = MediaType.parseMediaType(contentType);
                if (MediaType.APPLICATION_PDF.includes(mt)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse media type: {}", contentType, e);
        }
        return hasExtension(filename, ".pdf");
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            return cleanPdfText(stripper.getText(document));
        } catch (IOException e) {
            throw new FileParseException("Failed to parse PDF file: " + safeFilename(filename), e);
        }
    }

    String cleanPdfText(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null || cleaned.isBlank()) {
            return cleaned;
        }

        String[] lines = cleaned.split("\\n", -1);
        if (!hasFragmentedLines(lines)) {
            return cleaned;
        }

        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        int previousLineLength = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                flushParagraph(paragraph, paragraphs);
                previousLineLength = 0;
                continue;
            }
            appendLine(paragraph, trimmed, previousLineLength);
            previousLineLength = trimmed.length();
        }
        flushParagraph(paragraph, paragraphs);
        return String.join("\n\n", paragraphs).trim();
    }

    private boolean hasFragmentedLines(String[] lines) {
        int contentLineCount = 0;
        int shortLineCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            contentLineCount++;
            if (trimmed.length() <= 3) {
                shortLineCount++;
            }
        }
        return contentLineCount >= 8 && (double) shortLineCount / contentLineCount >= 0.35d;
    }

    private void appendLine(StringBuilder paragraph, String line, int previousLineLength) {
        if (paragraph.isEmpty()) {
            paragraph.append(line);
            return;
        }

        String previous = paragraph.toString();
        char previousLast = previous.charAt(previous.length() - 1);
        char nextFirst = line.charAt(0);
        if (isLeadingPunctuation(nextFirst) || isOpeningBracket(previousLast)
                || previousLineLength <= 3 || line.length() <= 3) {
            paragraph.append(line);
            return;
        }

        paragraph.append(' ').append(line);
    }

    private boolean isLeadingPunctuation(char value) {
        return ",.;:!?，。！？)]}".indexOf(value) >= 0;
    }

    private boolean isOpeningBracket(char value) {
        return "([{".indexOf(value) >= 0;
    }

    private void flushParagraph(StringBuilder paragraph, List<String> paragraphs) {
        if (paragraph.isEmpty()) {
            return;
        }
        paragraphs.add(paragraph.toString());
        paragraph.setLength(0);
    }
}
