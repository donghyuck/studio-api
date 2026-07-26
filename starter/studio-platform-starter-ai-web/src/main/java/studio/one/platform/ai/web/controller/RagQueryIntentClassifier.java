package studio.one.platform.ai.web.controller;

import java.util.Locale;

/**
 * Classifies an object-scoped RAG query without adding another model call.
 */
public interface RagQueryIntentClassifier {

    Classification classify(String query);

    static RagQueryIntentClassifier rules() {
        return new RuleBasedRagQueryIntentClassifier();
    }

    enum Intent {
        CONTENT_QA,
        DOCUMENT_METADATA,
        DOCUMENT_SUMMARY,
        KEY_POINTS,
        INTERPRETIVE_ANALYSIS
    }

    record Classification(Intent intent, double confidence, String reason) {
    }
}

final class RuleBasedRagQueryIntentClassifier implements RagQueryIntentClassifier {

    @Override
    public Classification classify(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (containsAny(normalized,
                "문서 제목", "책 제목", "제목이 무엇", "제목은 무엇",
                "저자가 누구", "저자는 누구", "작가가 누구", "작가는 누구", "누가 쓴",
                "편집자가 누구", "번역자가 누구", "출판사가 어디", "발행일", "발간일",
                "제출일", "저자 소속", "발행 기관", "isbn", "doi",
                "document title", "book title", "author of", "who wrote", "publisher",
                "publication date", "author affiliation")) {
            return new Classification(Intent.DOCUMENT_METADATA, 0.96d, "DOCUMENT_METADATA_PHRASE");
        }
        if (containsAny(normalized,
                "핵심 내용", "핵심내용", "핵심 요점", "핵심요점", "주요 내용", "주요내용",
                "중요 내용", "중요내용", "핵심 주제", "핵심주제", "주요 주제", "주요주제",
                "문서의 주제", "책의 주제", "저자의 논지", "핵심 논지", "핵심논지",
                "목차", "key point", "key points", "highlights", "main idea", "main ideas", "main theme")) {
            return new Classification(Intent.KEY_POINTS, 0.95d, "KEY_POINT_PHRASE");
        }
        if (containsAny(normalized,
                "줄거리", "전체 내용", "전체내용", "문서 요약", "요약해", "요약하여",
                "요약해줘", "요약해 주세요", "plot summary", "summarize", "synopsis", "give me an overview")) {
            return new Classification(Intent.DOCUMENT_SUMMARY, 0.95d, "SUMMARY_PHRASE");
        }
        if (containsAny(normalized,
                "mbti", "성격 유형", "성격유형", "어떤 성격", "주인공의 성격", "인물의 성격",
                "성격은", "성격을 분석", "성격 분석", "인물 분석", "인물을 분석", "동기를 분석",
                "상징을 분석", "의미를 해석", "추정해",
                "personality type", "character analysis", "analyze the character", "interpret the character",
                "character motivation", "symbolism")) {
            return new Classification(Intent.INTERPRETIVE_ANALYSIS, 0.92d, "INTERPRETIVE_PHRASE");
        }
        return new Classification(Intent.CONTENT_QA, 0.80d, "DEFAULT_CONTENT_QA");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
