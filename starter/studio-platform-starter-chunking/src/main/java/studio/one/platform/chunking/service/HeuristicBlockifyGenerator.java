package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import studio.one.platform.chunking.core.NormalizedBlock;

public class HeuristicBlockifyGenerator implements BlockifyGenerator {

    @Override
    public List<BlockifyBlock> generate(BlockifyGenerationRequest request) {
        String sourceText = sourceText(request.blocks());
        if (sourceText.isBlank()) {
            return List.of();
        }
        String title = firstNonBlank(request.headingPath(), "문서 핵심 내용");
        String answer = firstSentence(sourceText, 500);
        if (answer.isBlank()) {
            answer = sourceText.length() > 500 ? sourceText.substring(0, 500).trim() : sourceText.trim();
        }
        String question = title + "에 대해 무엇을 확인해야 하는가?";
        List<String> keywords = keywords(title, answer);
        BlockifySourceEvidence evidence = evidence(request, sourceText);
        return List.of(new BlockifyBlock(
                title,
                question,
                answer,
                keywords,
                List.of("blockify-poc"),
                List.of(evidence),
                0.8d));
    }

    private String sourceText(List<NormalizedBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        return blocks.stream()
                .map(NormalizedBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String firstSentence(String text, int maxLength) {
        String normalized = text == null ? "" : text.replace('\r', '\n').trim();
        if (normalized.isBlank()) {
            return "";
        }
        int limit = Math.min(normalized.length(), Math.max(1, maxLength));
        int sentenceEnd = -1;
        for (String marker : List.of(". ", "? ", "! ", "다. ", "요. ", "\n")) {
            int candidate = normalized.indexOf(marker);
            if (candidate >= 0 && candidate < limit) {
                int end = candidate + marker.trim().length();
                sentenceEnd = sentenceEnd < 0 ? end : Math.min(sentenceEnd, end);
            }
        }
        if (sentenceEnd > 0) {
            return normalized.substring(0, Math.min(sentenceEnd, normalized.length())).trim();
        }
        return normalized.substring(0, limit).trim();
    }

    private List<String> keywords(String title, String answer) {
        List<String> keywords = new ArrayList<>();
        for (String token : (title + " " + answer).split("[\\s,.;:()\\[\\]{}<>\"']+")) {
            String normalized = token.trim();
            if (normalized.length() < 2) {
                continue;
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (List.of("the", "and", "for", "with", "this", "that").contains(lower)) {
                continue;
            }
            if (!keywords.contains(normalized)) {
                keywords.add(normalized);
            }
            if (keywords.size() >= 5) {
                break;
            }
        }
        return List.copyOf(keywords);
    }

    private BlockifySourceEvidence evidence(BlockifyGenerationRequest request, String text) {
        NormalizedBlock first = request.blocks().isEmpty() ? null : request.blocks().get(0);
        NormalizedBlock last = request.blocks().isEmpty() ? first : request.blocks().get(request.blocks().size() - 1);
        List<String> blockIds = request.blocks().stream()
                .map(NormalizedBlock::blockIds)
                .flatMap(List::stream)
                .distinct()
                .toList();
        return new BlockifySourceEvidence(
                firstSentence(text, 300),
                first == null ? null : first.order(),
                null,
                null,
                first == null ? null : first.page(),
                first == null ? null : first.slide(),
                headingPath(request.headingPath()),
                request.sectionId(),
                blockIds.isEmpty() && last != null ? List.of(last.effectiveSourceRef()) : blockIds);
    }

    private List<String> headingPath(String headingPath) {
        if (headingPath == null || headingPath.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(headingPath.split(">"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
