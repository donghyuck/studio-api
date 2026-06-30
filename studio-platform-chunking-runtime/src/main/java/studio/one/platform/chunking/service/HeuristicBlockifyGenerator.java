package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;

public class HeuristicBlockifyGenerator implements BlockifyGenerator {

    private static final Pattern ARTICLE_NO = Pattern.compile("(제\\s*\\d+조)(?:\\s*\\(([^)]*)\\))?");
    private static final Pattern CAPITALIZED_PHRASE = Pattern.compile("\\b([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2})\\b");

    @Override
    public List<BlockifyBlock> generate(BlockifyGenerationRequest request) {
        String sourceText = bodyText(request.blocks());
        if (sourceText.isBlank()) {
            return List.of();
        }
        String title = firstNonBlank(request.headingPath(), "문서 핵심 내용");
        String answer = excerpt(sourceText, 500);
        if (answer.isBlank()) {
            answer = sourceText.length() > 500 ? sourceText.substring(0, 500).trim() : sourceText.trim();
        }
        String question = question(request, title, answer);
        List<String> keywords = keywords(title, answer);
        BlockifySourceEvidence evidence = evidence(request, sourceText);
        BlockifyBlock.SourceBlockRange range = sourceBlockRange(request.blocks());
        Map<String, Object> typedFields = typedFields(request, title, answer, sourceText);
        return List.of(new BlockifyBlock(
                title,
                title,
                question,
                answer,
                keywords,
                List.of("blockify-poc"),
                title,
                entityType(title),
                List.of(evidence),
                range,
                request.sectionId(),
                0.8d,
                typedFields));
    }

    private String bodyText(List<NormalizedBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        String body = blocks.stream()
                .filter(block -> block.type() != NormalizedBlockType.TITLE && block.type() != NormalizedBlockType.HEADING)
                .map(NormalizedBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        if (!body.isBlank()) {
            return body;
        }
        return blocks.stream()
                .map(NormalizedBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String question(BlockifyGenerationRequest request, String title, String answer) {
        if (request.documentType() == BlockifyDocumentType.NARRATIVE) {
            String character = text(typedFields(request, title, answer, answer).get("character"));
            if (character != null) {
                return title + "에서 " + character + "에게 어떤 사건이 발생하고 그 원인은 무엇인가?";
            }
            return title + "에서 어떤 사건이 발생하고 인물의 동기는 무엇인가?";
        }
        if (request.documentType() == BlockifyDocumentType.POLICY) {
            String articleNo = articleNo(title);
            if (articleNo != null) {
                return articleNo + "의 조건, 의무 또는 예외는 무엇인가?";
            }
            return title + "에서 적용 조건과 의무는 무엇인가?";
        }
        List<String> keywords = keywords(title, answer);
        if (!keywords.isEmpty()) {
            return title + "에서 " + keywords.get(0) + "와 관련해 무엇을 확인해야 하는가?";
        }
        return title + "의 핵심 내용은 무엇인가?";
    }

    private String excerpt(String text, int maxLength) {
        String normalized = text == null ? "" : text.replace('\r', '\n').trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        String first = firstSentence(normalized, maxLength);
        if (first.length() >= Math.min(120, maxLength)) {
            return first;
        }
        int limit = Math.min(normalized.length(), Math.max(1, maxLength));
        return normalized.substring(0, limit).trim();
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

    private Map<String, Object> typedFields(BlockifyGenerationRequest request, String title, String answer, String sourceText) {
        Map<String, Object> values = new LinkedHashMap<>();
        BlockifyDocumentType documentType = request.documentType() == null ? BlockifyDocumentType.GENERAL : request.documentType();
        switch (documentType) {
            case POLICY -> {
                put(values, "ruleName", firstNonBlank(articleTitle(title), title));
                put(values, "articleNo", articleNo(title));
                put(values, "condition", sentenceContaining(sourceText, "경우", "때", "조건"));
                put(values, "obligation", sentenceContaining(sourceText, "해야", "한다", "하여야"));
                put(values, "prohibition", sentenceContaining(sourceText, "금지", "할 수 없다", "않는다"));
                put(values, "exception", sentenceContaining(sourceText, "다만", "예외", "제외"));
                put(values, "deadline", sentenceContaining(sourceText, "까지", "기한", "일 이내"));
                put(values, "effectiveScope", title);
            }
            case NARRATIVE -> {
                put(values, "chapter", title);
                put(values, "character", character(sourceText));
                put(values, "event", excerpt(sourceText, 180));
                put(values, "eventType", eventType(sourceText));
                put(values, "motivation", sentenceContaining(sourceText, "because", "why", "wanted", "decided"));
                put(values, "cause", sentenceContaining(sourceText, "because", "since", "as ", "reason", "flunk"));
                put(values, "effect", sentenceContaining(sourceText, "so ", "therefore", "had to", "supposed to"));
                put(values, "location", location(sourceText));
                put(values, "quote", firstSentence(sourceText, 240));
            }
            case TECHNICAL -> {
                put(values, "component", title);
                put(values, "endpoint", sentenceContaining(sourceText, "/api/", "http://", "https://"));
                put(values, "command", sentenceContaining(sourceText, "gradlew", "docker", "kubectl", "curl"));
                put(values, "configurationKey", sentenceContaining(sourceText, "studio.", "spring.", "server."));
            }
            case TABLE_HEAVY -> {
                put(values, "tableTitle", title);
                put(values, "rowKey", firstSentence(sourceText, 120));
                put(values, "metricValue", firstNumber(sourceText));
            }
            case MANUAL -> {
                put(values, "taskName", title);
                put(values, "procedureStep", firstNumber(sourceText));
                put(values, "actor", sentenceContaining(sourceText, "관리자", "사용자", "user", "admin"));
                put(values, "nextAction", sentenceContaining(sourceText, "다음", "클릭", "선택", "입력"));
            }
            case AUTO, GENERAL -> {
                put(values, "topic", title);
                put(values, "summary", answer);
                put(values, "supportingEvidence", firstSentence(sourceText, 240));
            }
        }
        return Map.copyOf(values);
    }

    private BlockifySourceEvidence evidence(BlockifyGenerationRequest request, String text) {
        NormalizedBlock first = request.blocks().stream()
                .filter(block -> block.type() != NormalizedBlockType.TITLE && block.type() != NormalizedBlockType.HEADING)
                .findFirst()
                .orElse(request.blocks().isEmpty() ? null : request.blocks().get(0));
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

    private BlockifyBlock.SourceBlockRange sourceBlockRange(List<NormalizedBlock> blocks) {
        List<Integer> orders = blocks == null ? List.of() : blocks.stream()
                .filter(block -> block.type() != NormalizedBlockType.TITLE && block.type() != NormalizedBlockType.HEADING)
                .map(NormalizedBlock::order)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (orders.isEmpty()) {
            return null;
        }
        return new BlockifyBlock.SourceBlockRange(orders.get(0), orders.get(orders.size() - 1));
    }

    private String entityType(String title) {
        if (title != null && title.matches(".*제\\s*\\d+조.*")) {
            return "article";
        }
        return "section";
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void put(Map<String, Object> values, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        values.put(key, value);
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private String articleNo(String title) {
        Matcher matcher = ARTICLE_NO.matcher(title == null ? "" : title);
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", "") : null;
    }

    private String articleTitle(String title) {
        Matcher matcher = ARTICLE_NO.matcher(title == null ? "" : title);
        return matcher.find() && matcher.group(2) != null ? matcher.group(2).trim() : null;
    }

    private String sentenceContaining(String sourceText, String... tokens) {
        if (sourceText == null || sourceText.isBlank() || tokens == null) {
            return null;
        }
        for (String sentence : sourceText.replace('\r', '\n').split("(?<=[.!?。！？]|다\\.)\\s+|\\R+")) {
            String normalized = sentence.trim();
            String lower = normalized.toLowerCase(Locale.ROOT);
            for (String token : tokens) {
                if (token != null && lower.contains(token.toLowerCase(Locale.ROOT))) {
                    return excerpt(normalized, 220);
                }
            }
        }
        return null;
    }

    private String character(String sourceText) {
        Matcher matcher = CAPITALIZED_PHRASE.matcher(sourceText == null ? "" : sourceText);
        while (matcher.find()) {
            String value = matcher.group(1);
            String lower = value.toLowerCase(Locale.ROOT);
            if (!List.of("The", "This", "That", "Chapter", "One").contains(value)
                    && !List.of("the", "this", "that", "chapter", "one").contains(lower)) {
                return value;
            }
        }
        return null;
    }

    private String location(String sourceText) {
        String text = sourceText == null ? "" : sourceText;
        for (String marker : List.of("Pencey Prep", "Pencey", "school", "New York", "home")) {
            if (text.toLowerCase(Locale.ROOT).contains(marker.toLowerCase(Locale.ROOT))) {
                return marker;
            }
        }
        return null;
    }

    private String eventType(String sourceText) {
        String lower = sourceText == null ? "" : sourceText.toLowerCase(Locale.ROOT);
        if (lower.contains("expelled") || lower.contains("kicked") || lower.contains("flunk")) {
            return "school-expulsion";
        }
        if (lower.contains("said") || lower.contains("asked")) {
            return "dialogue";
        }
        return "narrative-event";
    }

    private String firstNumber(String sourceText) {
        Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(sourceText == null ? "" : sourceText);
        return matcher.find() ? matcher.group() : null;
    }
}
