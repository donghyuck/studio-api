package studio.one.platform.chunking.service;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.Chunker;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;

public class RecursiveChunker implements Chunker {

    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");

    private final int defaultMaxSize;

    private final int defaultOverlap;

    public RecursiveChunker(int defaultMaxSize, int defaultOverlap) {
        if (defaultMaxSize <= 0) {
            throw new IllegalArgumentException("defaultMaxSize must be positive");
        }
        if (defaultOverlap < 0) {
            throw new IllegalArgumentException("defaultOverlap cannot be negative");
        }
        this.defaultMaxSize = defaultMaxSize;
        this.defaultOverlap = Math.min(defaultOverlap, defaultMaxSize - 1);
    }

    @Override
    public ChunkingStrategyType strategy() {
        return ChunkingStrategyType.RECURSIVE;
    }

    @Override
    public List<Chunk> chunk(ChunkingContext context) {
        String text = normalize(context.text());
        if (text.isBlank()) {
            return List.of();
        }

        int maxSize = effectiveMaxSize(context.maxSize());
        int overlap = effectiveOverlap(context.overlap(), maxSize);
        List<String> segments = splitRecursively(text, maxSize);
        return pack(context, segments, maxSize, overlap);
    }

    private List<Chunk> pack(ChunkingContext context, List<String> segments, int maxSize, int overlap) {
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int order = 0;
        int cursor = 0;

        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            String separator = current.length() == 0 ? "" : separatorBetween(current, segment);
            if (current.length() > 0 && current.length() + separator.length() + segment.length() > maxSize) {
                String content = current.toString().trim();
                chunks.add(chunk(context, content, order++, cursor, cursor + content.length()));
                current = new StringBuilder(tailForOverlap(content, overlap));
                cursor += Math.max(content.length() - current.length(), 0);
                separator = current.length() == 0 ? "" : separatorBetween(current, segment);
                if (current.length() + separator.length() + segment.length() > maxSize) {
                    current = new StringBuilder();
                    separator = "";
                }
            }
            if (current.length() > 0 && separator.length() > 0) {
                current.append(separator);
            }
            current.append(segment);
        }

        if (current.length() > 0) {
            String content = current.toString().trim();
            chunks.add(chunk(context, content, order, cursor, cursor + content.length()));
        }
        linkNeighbors(context, chunks);
        return chunks;
    }

    private Chunk chunk(ChunkingContext context, String content, int order, int startOffset, int endOffset) {
        String id = chunkId(context.sourceDocumentId(), order);
        ChunkMetadata metadata = ChunkMetadata.builder(strategy(), order)
                .sourceDocumentId(context.sourceDocumentId())
                .chunkType(ChunkType.CHILD)
                .objectType(context.objectType())
                .objectId(context.objectId())
                .startOffset(startOffset)
                .endOffset(endOffset)
                .charCount(content.length())
                .build();
        return Chunk.of(id, content, metadata);
    }

    private void linkNeighbors(ChunkingContext context, List<Chunk> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            String previousChunkId = i == 0 ? null : chunkId(context.sourceDocumentId(), i - 1);
            String nextChunkId = i == chunks.size() - 1 ? null : chunkId(context.sourceDocumentId(), i + 1);
            ChunkMetadata metadata = ChunkMetadata.builder(chunk.metadata().strategy(), chunk.metadata().order())
                    .sourceDocumentId(chunk.metadata().sourceDocumentId())
                    .parentId(chunk.metadata().parentId())
                    .chunkType(chunk.metadata().chunkType())
                    .parentChunkId(chunk.metadata().parentChunkId())
                    .previousChunkId(previousChunkId)
                    .nextChunkId(nextChunkId)
                    .section(chunk.metadata().section())
                    .objectType(chunk.metadata().objectType())
                    .objectId(chunk.metadata().objectId())
                    .startOffset(chunk.metadata().startOffset())
                    .endOffset(chunk.metadata().endOffset())
                    .tokenCount(chunk.metadata().tokenCount())
                    .charCount(chunk.metadata().charCount())
                    .blockIds(chunk.metadata().blockIds())
                    .confidence(chunk.metadata().confidence())
                    .attributes(chunk.metadata().attributes())
                    .build();
            chunks.set(i, Chunk.of(chunk.id(), chunk.content(), metadata));
        }
    }

    private List<String> splitRecursively(String text, int maxSize) {
        if (text.length() <= maxSize) {
            return List.of(text);
        }

        List<String> byParagraph = splitByBlankParagraph(text);
        if (isUsefulSplit(byParagraph)) {
            return splitAll(byParagraph, maxSize);
        }

        List<String> byNewline = splitByDelimiter(text, "\n");
        if (isUsefulSplit(byNewline)) {
            return splitAll(byNewline, maxSize);
        }

        List<String> bySentence = splitBySentence(text);
        if (isUsefulSplit(bySentence)) {
            return splitAll(bySentence, maxSize);
        }

        List<String> byWhitespace = splitByWhitespace(text);
        if (isUsefulSplit(byWhitespace)) {
            return splitAll(byWhitespace, maxSize);
        }

        return splitFixed(text, maxSize);
    }

    private List<String> splitAll(List<String> segments, int maxSize) {
        List<String> results = new ArrayList<>();
        for (String segment : segments) {
            results.addAll(splitRecursively(segment.trim(), maxSize));
        }
        return results;
    }

    private boolean isUsefulSplit(List<String> segments) {
        return segments.size() > 1;
    }

    private List<String> splitByBlankParagraph(String text) {
        return splitByRegex(text, "\\n\\s*\\n");
    }

    private List<String> splitByDelimiter(String text, String delimiter) {
        return splitByRegex(text, Pattern.quote(delimiter));
    }

    private List<String> splitBySentence(String text) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(text);
        List<String> sentences = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isBlank()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private List<String> splitByWhitespace(String text) {
        return splitByRegex(text, "\\s+");
    }

    private List<String> splitByRegex(String text, String regex) {
        String[] split = text.split(regex);
        List<String> segments = new ArrayList<>(split.length);
        for (String segment : split) {
            String normalized = segment.trim();
            if (!normalized.isBlank()) {
                segments.add(normalized);
            }
        }
        return segments;
    }

    private List<String> splitFixed(String text, int maxSize) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxSize, text.length());
            segments.add(text.substring(start, end).trim());
            start = end;
        }
        return segments;
    }

    private String tailForOverlap(String content, int overlap) {
        if (overlap <= 0) {
            return "";
        }
        if (content.length() <= overlap) {
            return content;
        }
        return content.substring(content.length() - overlap);
    }

    private String separatorBetween(CharSequence current, String next) {
        if (current.length() == 0 || next.isBlank()) {
            return "";
        }
        return " ";
    }

    private int effectiveMaxSize(int requestedMaxSize) {
        return requestedMaxSize <= 0 ? defaultMaxSize : requestedMaxSize;
    }

    private int effectiveOverlap(int requestedOverlap, int maxSize) {
        int overlap = requestedOverlap < 0 ? defaultOverlap : requestedOverlap;
        return Math.min(overlap, maxSize - 1);
    }

    private String chunkId(String sourceDocumentId, int order) {
        String prefix = sourceDocumentId == null || sourceDocumentId.isBlank() ? "document" : sourceDocumentId;
        return prefix + "-" + order;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        return MULTI_BLANK_LINES.matcher(normalized).replaceAll("\n\n");
    }
}
