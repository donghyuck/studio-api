package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

public class MarkdownTextBlockParser {

    public NormalizedDocument parse(String markdown, String documentId, String filename, String sourceFormat) {
        String text = markdown == null ? "" : markdown;
        List<NormalizedBlock> blocks = new ArrayList<>();
        String[] lines = text.split("\\n", -1);
        int order = 0;
        String headingPath = "";
        int index = 0;
        while (index < lines.length) {
            String line = lines[index];
            if (line.isBlank()) {
                index++;
                continue;
            }
            if (isHeading(line)) {
                int level = headingLevel(line);
                String title = line.replaceFirst("^#{1,6}\\s+", "").trim();
                headingPath = title;
                blocks.add(NormalizedBlock.builder(level == 1 ? NormalizedBlockType.TITLE : NormalizedBlockType.HEADING,
                        title)
                        .id(documentId + ":md:" + order)
                        .order(order++)
                        .headingPath(headingPath)
                        .metadata(Map.of("level", level, "source", "markdown"))
                        .build());
                index++;
                continue;
            }
            if (isTableStart(lines, index)) {
                int start = index;
                index += 2;
                while (index < lines.length && isTableLine(lines[index])) {
                    index++;
                }
                String table = String.join("\n", List.of(lines).subList(start, index)).trim();
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("markdown", table);
                metadata.put("source", "markdown");
                blocks.add(NormalizedBlock.builder(NormalizedBlockType.TABLE, table)
                        .id(documentId + ":md:" + order)
                        .order(order++)
                        .headingPath(headingPath)
                        .metadata(metadata)
                        .build());
                continue;
            }
            if (isListItem(line)) {
                blocks.add(NormalizedBlock.builder(NormalizedBlockType.LIST_ITEM,
                        line.replaceFirst("^\\s*[-*+]\\s+", "").trim())
                        .id(documentId + ":md:" + order)
                        .order(order++)
                        .headingPath(headingPath)
                        .metadata(Map.of("source", "markdown"))
                        .build());
                index++;
                continue;
            }
            int start = index;
            index++;
            while (index < lines.length && !lines[index].isBlank() && !isHeading(lines[index])
                    && !isListItem(lines[index]) && !isTableStart(lines, index)) {
                index++;
            }
            String paragraph = String.join("\n", List.of(lines).subList(start, index)).trim();
            blocks.add(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, paragraph)
                    .id(documentId + ":md:" + order)
                    .order(order++)
                    .headingPath(headingPath)
                    .metadata(Map.of("source", "markdown"))
                    .build());
        }
        if (blocks.isEmpty() && !text.isBlank()) {
            blocks.add(NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, text)
                    .id(documentId)
                    .order(0)
                    .metadata(Map.of("source", "markdown-fallback"))
                    .build());
        }
        return NormalizedDocument.builder(documentId)
                .plainText(text)
                .sourceFormat(sourceFormat)
                .filename(filename)
                .blocks(blocks)
                .metadata(Map.of("contentFormat", "markdown"))
                .build();
    }

    private boolean isHeading(String line) {
        return line != null && line.matches("^#{1,6}\\s+.+");
    }

    private int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        return Math.max(1, Math.min(6, level));
    }

    private boolean isListItem(String line) {
        return line != null && line.matches("^\\s*[-*+]\\s+.+");
    }

    private boolean isTableStart(String[] lines, int index) {
        return index + 1 < lines.length && isTableLine(lines[index]) && isTableSeparator(lines[index + 1]);
    }

    private boolean isTableLine(String line) {
        return line != null && line.trim().startsWith("|") && line.trim().endsWith("|");
    }

    private boolean isTableSeparator(String line) {
        if (line == null || !line.contains("|")) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String[] cells = trimmed.split("\\|");
        if (cells.length == 0) {
            return false;
        }
        for (String cell : cells) {
            if (!cell.trim().matches(":?-{3,}:?")) {
                return false;
            }
        }
        return true;
    }
}
