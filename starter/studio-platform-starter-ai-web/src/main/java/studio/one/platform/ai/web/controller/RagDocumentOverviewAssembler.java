package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.chunking.core.ChunkMetadata;

/**
 * Reconstructs ordered object chunks for whole-document overview questions.
 */
final class RagDocumentOverviewAssembler {

    private static final int MAX_REFERENCE_CHUNKS = 8;
    private static final String HEADER_PREFIX = """
            다음은 하나의 원본 문서를 처음부터 끝까지 순서대로 재구성한 내용입니다.
            문서 안에서 언급되는 영화, 책, 이야기의 줄거리를 원본 문서 전체의 줄거리와 혼동하지 마세요.
            """;

    Assembly assemble(List<RagSearchResult> results, int maxChars, boolean allChunksLoaded) {
        List<RagSearchResult> ordered = ordered(results);
        if (ordered.isEmpty()) {
            return new Assembly("", List.of(), 0, false);
        }
        Map<String, Object> firstMetadata = ordered.get(0).metadata();
        String header = header(firstMetadata);
        String reconstructed = reconstruct(ordered);
        boolean fullCoverage = allChunksLoaded && header.length() + reconstructed.length() <= maxChars;
        String body = fullCoverage
                ? reconstructed
                : representativeCoverage(ordered, Math.max(0, maxChars - header.length()));
        String context = header + body;
        return new Assembly(
                context,
                representativeReferences(ordered),
                ordered.size(),
                fullCoverage);
    }

    private String header(Map<String, Object> metadata) {
        StringBuilder header = new StringBuilder(HEADER_PREFIX);
        appendIdentity(header, "문서 제목", firstText(metadata, "documentTitle", "title"));
        appendIdentity(header, "원본 파일", firstText(metadata,
                "originalFileName", "sourceFileName", "filename", "fileName", "sourceName", "name"));
        header.append("[문서 본문]\n");
        return header.toString();
    }

    private List<RagSearchResult> representativeReferences(List<RagSearchResult> ordered) {
        int count = Math.min(ordered.size(), MAX_REFERENCE_CHUNKS);
        List<RagSearchResult> references = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int sourceIndex = count == 1
                    ? 0
                    : (int) Math.round((double) index * (ordered.size() - 1) / (count - 1));
            references.add(ordered.get(sourceIndex));
        }
        return List.copyOf(references);
    }

    private void appendIdentity(StringBuilder target, String label, String value) {
        if (value != null) {
            target.append(label).append(": ").append(value).append('\n');
        }
    }

    private String firstText(Map<String, Object> metadata, String... keys) {
        Map<String, Object> values = metadata == null ? Map.of() : metadata;
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !value.toString().isBlank()) {
                String normalized = value.toString().replaceAll("\\s+", " ").trim();
                return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
            }
        }
        return null;
    }

    private List<RagSearchResult> ordered(List<RagSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<RagSearchResult> ordered = new ArrayList<>(results);
        ordered.sort(Comparator.comparingInt(result -> order(result, Integer.MAX_VALUE)));
        return ordered;
    }

    private String reconstruct(List<RagSearchResult> ordered) {
        StringBuilder text = new StringBuilder();
        Integer previousEnd = null;
        for (RagSearchResult result : ordered) {
            String content = Objects.toString(result.content(), "");
            Integer start = integer(result.metadata(), ChunkMetadata.KEY_START_OFFSET, "startOffset");
            Integer end = integer(result.metadata(), ChunkMetadata.KEY_END_OFFSET, "endOffset");
            int overlap = previousEnd != null && start != null ? Math.max(0, previousEnd - start) : 0;
            if (overlap < content.length()) {
                append(text, content.substring(overlap));
            }
            if (end != null) {
                previousEnd = previousEnd == null ? end : Math.max(previousEnd, end);
            }
        }
        return text.toString();
    }

    private String representativeCoverage(List<RagSearchResult> ordered, int budget) {
        if (budget <= 0) {
            return "";
        }
        int sampleCount = Math.min(ordered.size(), 48);
        int excerptSize = Math.max(80, budget / Math.max(1, sampleCount) - 40);
        StringBuilder text = new StringBuilder(Math.min(budget, 16_384));
        for (int index = 0; index < sampleCount && text.length() < budget; index++) {
            int sourceIndex = sampleCount == 1
                    ? 0
                    : (int) Math.round((double) index * (ordered.size() - 1) / (sampleCount - 1));
            RagSearchResult result = ordered.get(sourceIndex);
            String content = Objects.toString(result.content(), "").strip();
            if (content.length() > excerptSize) {
                content = content.substring(0, excerptSize);
            }
            String excerpt = "\n[chunk " + order(result, sourceIndex) + "] " + content;
            if (text.length() + excerpt.length() > budget) {
                excerpt = excerpt.substring(0, Math.max(0, budget - text.length()));
            }
            text.append(excerpt);
        }
        return text.toString();
    }

    private void append(StringBuilder target, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!target.isEmpty() && !Character.isWhitespace(target.charAt(target.length() - 1))) {
            target.append('\n');
        }
        target.append(content.strip());
    }

    private int order(RagSearchResult result, int fallback) {
        Integer value = integer(result.metadata(), ChunkMetadata.KEY_CHUNK_ORDER, "chunkOrder", "chunkIndex");
        return value == null ? fallback : value;
    }

    private Integer integer(Map<String, Object> metadata, String... keys) {
        Map<String, Object> values = metadata == null ? Map.of() : metadata;
        for (String key : keys) {
            Object value = values.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.valueOf(value.toString().trim());
                } catch (NumberFormatException ignored) {
                    // Try the next key.
                }
            }
        }
        return null;
    }

    record Assembly(
            String context,
            List<RagSearchResult> references,
            int sourceChunkCount,
            boolean fullCoverage) {
    }
}
