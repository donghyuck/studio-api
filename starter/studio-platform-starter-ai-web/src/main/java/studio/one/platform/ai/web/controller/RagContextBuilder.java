package studio.one.platform.ai.web.controller;

import java.util.List;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.core.rag.RagSearchResult;

/**
 * Builds bounded RAG context prompts for chat completion requests.
 */
public class RagContextBuilder {

    private static final String NO_CONTEXT_MESSAGE = "참고할 문서가 없습니다. 일반적으로 답변하세요.";
    private static final String HEADER = "다음 문서 내용을 참고해 답변하세요:\n";

    private final int maxChunks;
    private final int maxChars;
    private final boolean includeScores;

    public RagContextBuilder(AiWebRagProperties properties) {
        this(properties.getContext().getMaxChunks(),
                properties.getContext().getMaxChars(),
                properties.getContext().isIncludeScores());
    }

    public RagContextBuilder(int maxChunks, int maxChars, boolean includeScores) {
        this.maxChunks = Math.max(0, maxChunks);
        this.maxChars = Math.max(0, maxChars);
        this.includeScores = includeScores;
    }

    public static RagContextBuilder defaults() {
        return new RagContextBuilder(8, 12_000, true);
    }

    public String build(List<RagSearchResult> results) {
        if (results == null || results.isEmpty() || maxChunks == 0 || maxChars == 0) {
            return NO_CONTEXT_MESSAGE;
        }
        StringBuilder sb = new StringBuilder(HEADER);
        int count = Math.min(maxChunks, results.size());
        for (int i = 0; i < count; i++) {
            RagSearchResult result = results.get(i);
            String chunk = formatChunk(i + 1, result);
            if (!appendWithinLimit(sb, chunk)) {
                break;
            }
        }
        String context = sb.toString().trim();
        return HEADER.trim().equals(context) ? NO_CONTEXT_MESSAGE : context;
    }

    private boolean appendWithinLimit(StringBuilder sb, String chunk) {
        int remaining = maxChars - sb.length();
        if (remaining <= 0) {
            return false;
        }
        if (chunk.length() <= remaining) {
            sb.append(chunk);
            return true;
        }
        sb.append(chunk, 0, remaining);
        return false;
    }

    private String formatChunk(int index, RagSearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(index).append("] docId=").append(result.documentId());
        if (includeScores) {
            sb.append(" score=").append(String.format("%.3f", result.score()));
        }
        sb.append("\n").append(result.content()).append("\n\n");
        return sb.toString();
    }
}
