package studio.one.platform.ai.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto;

public final class DocumentQuestionSuggestionPolicy {

    public static final String CONTRACT_VERSION = "rag-question-suggestions-v2";
    public static final int MAX_SUGGESTIONS = 3;
    static final int MAX_CANDIDATES = 200;
    static final int MAX_KEYWORD_CHARS = 80;
    static final int MAX_QUESTION_CHARS = 180;

    static final String KEYWORD_EXPLANATION_TEMPLATE =
            "문서에서 '%s'의 핵심 의미를 설명해줘";
    static final String KEYWORD_RELATION_TEMPLATE =
            "문서 근거를 바탕으로 '%s' 및 '%s' 사이의 관계를 설명해줘";
    static final String KEYWORD_SUMMARY_TEMPLATE =
            "문서에서 '%s' 관련 핵심 내용을 정리해줘";
    private static final String FINGERPRINT_INPUT = String.join("|",
            CONTRACT_VERSION,
            String.valueOf(MAX_SUGGESTIONS),
            String.valueOf(MAX_CANDIDATES),
            String.valueOf(MAX_KEYWORD_CHARS),
            String.valueOf(MAX_QUESTION_CHARS),
            KEYWORD_EXPLANATION_TEMPLATE,
            KEYWORD_RELATION_TEMPLATE,
            KEYWORD_SUMMARY_TEMPLATE,
            "IDEA_BLOCK_QUESTION,DOCUMENT_KEYWORDS,KEY_POINTS,CHUNK_KEYWORDS");
    public static final String FINGERPRINT = "sha256:" + sha256(FINGERPRINT_INPUT);

    public DocumentQuestionSuggestionsResponseDto.Policy snapshot() {
        return new DocumentQuestionSuggestionsResponseDto.Policy(
                CONTRACT_VERSION,
                FINGERPRINT,
                MAX_SUGGESTIONS);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
