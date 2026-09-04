package studio.one.platform.ai.web.controller;

import java.util.Locale;
import java.util.regex.Pattern;

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
        INTERPRETIVE_ANALYSIS,
        FACTUAL_LIST
    }

    record Classification(Intent intent, double confidence, String reason) {
    }
}

final class RuleBasedRagQueryIntentClassifier implements RagQueryIntentClassifier {

    private static final Pattern RELATION_MARKER = Pattern.compile(
            "(?:관련\\s*있는|관련된|연관\\s*있는|연관된|언급된|등장하는"
                    + "|관련\\s+|연관\\s+"
                    + "|related\\s+to|related\\s+|associated\\s+with|mentioned\\s+in)");
    private static final Pattern RELATIONAL_TOPIC_ENDING = Pattern.compile(
            ".*\\s+[^\\s?？.]{1,40}(?:은|는|이|가|들은|들이)[?？]?$");
    private static final Pattern POSSESSIVE_TOPIC_ENDING = Pattern.compile(
            ".*\\S+의\\s+\\S+(?:은|는|이|가)[?？]?$");

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
                "상징을 분석", "의미를 해석", "추정해", "이유는", "이유를", "왜 ",
                "권장 도서", "권장도서", "읽을 가치", "작품의 가치", "문서의 가치",
                "의의는", "의의를", "교훈은", "교훈을", "시사점", "평가해",
                "personality type", "character analysis", "analyze the character", "interpret the character",
                "character motivation", "symbolism", "why is", "why does", "why should",
                "recommended reading", "significance", "literary value")) {
            return new Classification(Intent.INTERPRETIVE_ANALYSIS, 0.92d, "INTERPRETIVE_PHRASE");
        }
        if (hasExplicitRelationalListCue(normalized)) {
            return new Classification(Intent.FACTUAL_LIST, 0.91d, "RELATIONAL_LIST_STRUCTURE");
        }
        if (normalized.matches(".*(?:은|는|이|가)\\s*[^?？.]+(?:인가|한가)[?？]?$")
                || normalized.contains("라고 볼 수 있는가")
                || normalized.contains("로 볼 수 있는가")
                || normalized.contains("으로 볼 수 있는가")
                || normalized.contains("로 평가할 수 있는가")
                || normalized.contains("으로 평가할 수 있는가")) {
            return new Classification(Intent.INTERPRETIVE_ANALYSIS, 0.90d, "EVALUATIVE_QUESTION");
        }
        if (looksLikeRelationalListQuery(normalized)) {
            return new Classification(Intent.FACTUAL_LIST, 0.91d, "RELATIONAL_LIST_STRUCTURE");
        }
        return new Classification(Intent.CONTENT_QA, 0.80d, "DEFAULT_CONTENT_QA");
    }

    private boolean looksLikeRelationalListQuery(String value) {
        if (containsAny(value,
                "which scholars",
                "mentioned researchers",
                "related scholars",
                "people associated with",
                "people related to")) {
            return true;
        }
        if (!RELATION_MARKER.matcher(value).find()) {
            return value.contains("누구들이");
        }
        if (hasExplicitRelationalListCue(value)) {
            return true;
        }
        return RELATIONAL_TOPIC_ENDING.matcher(value).matches()
                && !POSSESSIVE_TOPIC_ENDING.matcher(value).matches();
    }

    private boolean hasExplicitRelationalListCue(String value) {
        return RELATION_MARKER.matcher(value).find()
                && containsAny(value,
                        "누구", "무엇", "어떤", "목록", "나열", "알려", "찾아", "정리", "말해",
                        "who", "which", "what", "list");
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
