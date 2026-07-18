package studio.one.platform.ai.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Reduces large ordered document contexts before the final overview answer.
 */
final class RagDocumentMapReduceOverview {

    private static final int MAX_CACHE_ENTRIES = 64;
    private static final int MAX_PARALLEL_SEGMENTS = 3;
    private static final String REDUCED_HEADER_PREFIX = """
            다음은 원본 문서를 처음부터 끝까지 나눈 구간별 요약입니다.
            각 구간 요약은 원본 순서대로 배치되어 있습니다. 문서 안에서 언급되는 영화, 책, 이야기를 원본 문서 전체와 혼동하지 마세요.
            """;

    private final Map<String, String> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    Reduction reduce(
            String objectType,
            String objectId,
            String provider,
            String model,
            String context,
            int thresholdChars,
            int segmentChars,
            Function<String, String> segmentSummarizer) {
        Objects.requireNonNull(segmentSummarizer, "segmentSummarizer");
        String source = Objects.toString(context, "");
        if (source.length() <= thresholdChars) {
            return Reduction.notApplied(source);
        }

        List<String> segments = split(source, segmentChars);
        String cacheKey = fingerprint(objectType, objectId, provider, model, source);
        synchronized (cache) {
            String cached = cache.get(cacheKey);
            if (cached != null) {
                return new Reduction(cached, true, true, segments.size());
            }
        }

        List<String> summaries = summarizeSegments(segments, segmentSummarizer);
        StringBuilder reduced = new StringBuilder(reducedHeader(source));
        for (int index = 0; index < segments.size(); index++) {
            reduced.append("\n[구간 ")
                    .append(index + 1)
                    .append('/')
                    .append(segments.size())
                    .append("]\n")
                    .append(summaries.get(index))
                    .append('\n');
        }
        String result = reduced.toString();
        synchronized (cache) {
            cache.put(cacheKey, result);
        }
        return new Reduction(result, true, false, segments.size());
    }

    private String reducedHeader(String source) {
        StringBuilder header = new StringBuilder(REDUCED_HEADER_PREFIX);
        appendIdentityLine(header, source, "문서 제목:");
        appendIdentityLine(header, source, "원본 파일:");
        header.append("[1]\n");
        return header.toString();
    }

    private void appendIdentityLine(StringBuilder target, String source, String prefix) {
        for (String line : source.lines().limit(12).toList()) {
            String normalized = line.strip();
            if (normalized.startsWith(prefix) && normalized.length() > prefix.length()) {
                target.append(normalized).append('\n');
                return;
            }
        }
    }

    private List<String> summarizeSegments(
            List<String> segments,
            Function<String, String> segmentSummarizer) {
        int parallelism = Math.min(MAX_PARALLEL_SEGMENTS, segments.size());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<String>> futures = new ArrayList<>(segments.size());
            for (int index = 0; index < segments.size(); index++) {
                int segmentIndex = index;
                futures.add(executor.submit(() -> validatedSummary(segmentSummarizer.apply(
                        mapPrompt(segments.get(segmentIndex), segmentIndex, segments.size())))));
            }
            List<String> summaries = new ArrayList<>(segments.size());
            for (Future<String> future : futures) {
                summaries.add(future.get());
            }
            return List.copyOf(summaries);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Document segment summary was interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Document segment summary failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private String validatedSummary(String value) {
        String summary = Objects.toString(value, "").strip();
        if (summary.isBlank()) {
            throw new IllegalStateException("Document segment summary was blank");
        }
        return summary;
    }

    private List<String> split(String context, int maxChars) {
        int limit = Math.max(1_000, maxChars);
        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < context.length()) {
            int end = Math.min(context.length(), start + limit);
            if (end < context.length()) {
                int paragraphBoundary = context.lastIndexOf('\n', end);
                if (paragraphBoundary > start + limit / 2) {
                    end = paragraphBoundary;
                }
            }
            segments.add(context.substring(start, end).strip());
            start = end;
            while (start < context.length() && Character.isWhitespace(context.charAt(start))) {
                start++;
            }
        }
        return List.copyOf(segments);
    }

    private String mapPrompt(String segment, int index, int total) {
        return """
                아래 내용은 하나의 원본 문서 중 %d/%d 구간입니다.
                이 구간의 핵심 주장, 개념, 절차 또는 사건과 인물의 변화, 다음 구간 이해에 필요한 연결 정보만 원문 근거대로 요약하세요.
                문서 속 인물이 언급하거나 감상하는 영화, 책, 꿈, 회상은 원본 문서의 주 줄거리와 구분해서 표시하세요.
                원문에 없는 장소, 진단, 관계, 동기를 추가하지 마세요. 1,200자 이내의 한국어로 작성하세요.

                %s
                """.formatted(index + 1, total, segment);
    }

    private String fingerprint(String objectType, String objectId, String provider, String model, String context) {
        String value = String.join("\u0000",
                Objects.toString(objectType, ""),
                Objects.toString(objectId, ""),
                Objects.toString(provider, ""),
                Objects.toString(model, ""),
                context);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                encoded.append(String.format("%02x", item));
            }
            return encoded.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    record Reduction(String context, boolean applied, boolean cacheHit, int segmentCount) {

        static Reduction notApplied(String context) {
            return new Reduction(context, false, false, 0);
        }
    }
}
