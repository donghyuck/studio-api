package studio.one.platform.textract.application.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.ExtractedTable;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;

public class MarkdownDocumentBuilder {

    private final MarkdownSanitizer sanitizer;
    private final MarkdownTableRenderer tableRenderer;

    public MarkdownDocumentBuilder() {
        this(new MarkdownSanitizer());
    }

    public MarkdownDocumentBuilder(MarkdownSanitizer sanitizer) {
        this.sanitizer = sanitizer;
        this.tableRenderer = new MarkdownTableRenderer(sanitizer);
    }

    public ParsedFile normalize(ParsedFile parsedFile) {
        if (parsedFile == null || !parsedFile.markdown().isBlank()) {
            return parsedFile;
        }

        Map<String, ExtractedTable> tables = new LinkedHashMap<>();
        parsedFile.tables().forEach(table -> tables.put(table.sourceRef(), table));
        List<ParsedBlock> blocks = parsedFile.blocks().stream()
                .sorted(Comparator.comparing(ParsedBlock::order, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        StringBuilder markdown = new StringBuilder();
        MarkdownLocatorBuilder locatorBuilder = new MarkdownLocatorBuilder();

        for (ParsedBlock block : blocks) {
            String rendered = render(block, tables.get(block.sourceRef()));
            if (rendered.isBlank()) {
                continue;
            }
            if (markdown.length() > 0) {
                markdown.append("\n\n");
            }
            int start = markdown.length();
            markdown.append(rendered);
            int end = markdown.length();

            locatorBuilder.record(block, start, end);
        }

        String normalized = sanitizer.sanitize(markdown.toString());
        if (normalized.isBlank()) {
            normalized = sanitizer.sanitize(parsedFile.plainText());
        }
        return parsedFile.withMarkdown(normalized, locatorBuilder.build());
    }

    private String render(ParsedBlock block, ExtractedTable table) {
        String text = sanitizer.sanitize(block.text());
        return switch (block.blockType()) {
            case TITLE -> heading(text, 1);
            case HEADING -> heading(text, headingLevel(block));
            case LIST_ITEM -> text.isBlank() ? "" : "- " + text.replace("\n", "\n  ");
            case TABLE -> table == null ? text : tableRenderer.render(table);
            case IMAGE -> text.isBlank() ? "" : "![" + sanitizer.inline(text) + "]()";
            case IMAGE_CAPTION -> text.isBlank() ? "" : "*" + text + "*";
            case OCR_TEXT -> text.isBlank() ? "" : "## OCR Text\n\n" + text;
            case PAGE -> text.isBlank() ? "" : "# Page " + defaultNumber(block.page()) + "\n\n" + text;
            default -> text;
        };
    }

    private int headingLevel(ParsedBlock block) {
        Object value = block.metadata().get("headingLevel");
        int level = value instanceof Number number ? number.intValue() : 2;
        return Math.max(1, Math.min(6, level));
    }

    private String heading(String text, int level) {
        return text.isBlank() ? "" : "#".repeat(level) + " " + text;
    }

    private int defaultNumber(Integer value) {
        return value == null ? 1 : value;
    }

}
