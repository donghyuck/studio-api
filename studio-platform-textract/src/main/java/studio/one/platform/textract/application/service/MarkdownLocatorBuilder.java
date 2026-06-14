package studio.one.platform.textract.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.MarkdownLocator;
import studio.one.platform.textract.domain.model.ParsedBlock;

public class MarkdownLocatorBuilder {

    private final Map<String, Range> ranges = new LinkedHashMap<>();
    private final List<MarkdownLocator> sections = new ArrayList<>();

    public void record(ParsedBlock block, int startOffset, int endOffset) {
        if (block.page() != null) {
            merge("page", block.page(), "", block, startOffset, endOffset);
        }
        if (block.slide() != null) {
            merge("slide", block.slide(), "", block, startOffset, endOffset);
        }
        if (block.blockType() == BlockType.TITLE || block.blockType() == BlockType.HEADING) {
            sections.add(new MarkdownLocator("section", null, block.text(), startOffset, endOffset,
                    block.sourceRef(), block.metadata()));
        }
    }

    public List<MarkdownLocator> build() {
        List<MarkdownLocator> locators = new ArrayList<>();
        ranges.values().forEach(range -> locators.add(range.toLocator()));
        locators.addAll(sections);
        return List.copyOf(locators);
    }

    private void merge(String type, Integer number, String title, ParsedBlock block, int startOffset, int endOffset) {
        String key = type + ":" + number;
        Range current = ranges.get(key);
        if (current == null) {
            ranges.put(key, new Range(type, number, title, startOffset, endOffset,
                    block.sourceRef(), block.metadata()));
            return;
        }
        current.endOffset = Math.max(current.endOffset, endOffset);
    }

    private static final class Range {
        private final String type;
        private final Integer number;
        private final String title;
        private final int startOffset;
        private int endOffset;
        private final String sourceRef;
        private final Map<String, Object> metadata;

        private Range(String type, Integer number, String title, int startOffset, int endOffset,
                String sourceRef, Map<String, Object> metadata) {
            this.type = type;
            this.number = number;
            this.title = title;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.sourceRef = sourceRef;
            this.metadata = metadata;
        }

        private MarkdownLocator toLocator() {
            return new MarkdownLocator(type, number, title, startOffset, endOffset, sourceRef, metadata);
        }
    }
}
