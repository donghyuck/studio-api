package studio.one.platform.ai.web.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRagRetrievalOptionsDto;
import studio.one.platform.ai.web.controller.ResolvedRagAnswerPolicy;
import studio.one.platform.ai.web.controller.ResolvedRagSourcePolicy;

public record RagAnswerCacheKey(String digest) {

    private static final String SCHEMA_VERSION = "v9";
    private static final String RAG_PROMPT_CONTRACT_VERSION = "rag-grounding-v9";
    private static final String RAG_VALIDATOR_VERSION = "rag-answer-validator-v3";

    public RagAnswerCacheKey {
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("digest must not be blank");
        }
    }

    public static RagAnswerCacheKey create(
            String authorizationScope,
            ChatRagRequestDto request,
            String resolvedQuestion,
            String chatDeployment,
            String contextFingerprint) {
        return create(
                authorizationScope,
                request,
                resolvedQuestion,
                chatDeployment,
                contextFingerprint,
                null,
                null);
    }

    public static RagAnswerCacheKey create(
            String authorizationScope,
            ChatRagRequestDto request,
            String resolvedQuestion,
            String chatDeployment,
            String contextFingerprint,
            ResolvedRagAnswerPolicy answerPolicy) {
        return create(
                authorizationScope,
                request,
                resolvedQuestion,
                chatDeployment,
                contextFingerprint,
                answerPolicy,
                null);
    }

    public static RagAnswerCacheKey create(
            String authorizationScope,
            ChatRagRequestDto request,
            String resolvedQuestion,
            String chatDeployment,
            String contextFingerprint,
            ResolvedRagAnswerPolicy answerPolicy,
            ResolvedRagSourcePolicy sourcePolicy) {
        Objects.requireNonNull(request, "request");
        StringBuilder canonical = new StringBuilder();
        append(canonical, SCHEMA_VERSION);
        append(canonical, raw(authorizationScope));
        append(canonical, raw(request.objectType()));
        append(canonical, raw(request.objectId()));
        append(canonical, normalizeQuestion(resolvedQuestion));
        append(canonical, raw(request.retrievalStrategy()));
        append(canonical, retrievalOptions(request.retrievalOptions()));
        append(canonical, raw(String.valueOf(request.ragTopK())));
        append(canonical, raw(String.valueOf(request.topK())));
        append(canonical, raw(String.valueOf(request.minScore())));
        append(canonical, raw(request.embeddingDeploymentId()));
        append(canonical, raw(request.embeddingProfileId()));
        append(canonical, raw(request.embeddingProvider()));
        append(canonical, raw(request.embeddingModel()));
        append(canonical, raw(chatDeployment));
        append(canonical, raw(request.chat().deploymentId()));
        append(canonical, raw(request.chat().model()));
        append(canonical, raw(String.valueOf(request.chat().temperature())));
        append(canonical, raw(String.valueOf(request.chat().topP())));
        append(canonical, raw(String.valueOf(request.chat().topK())));
        append(canonical, raw(String.valueOf(request.chat().maxOutputTokens())));
        append(canonical, raw(String.valueOf(request.chat().stopSequences())));
        append(canonical, raw(request.chat().systemPrompt()));
        append(canonical, raw(String.valueOf(request.chat().messages())));
        append(canonical, raw(contextFingerprint));
        append(canonical, answerPolicy == null ? "" : answerPolicy.effectiveMode().name());
        append(canonical, answerPolicy == null ? "" : answerPolicy.fingerprint());
        append(canonical, raw(request.sourceScope()));
        append(canonical, raw(String.valueOf(request.externalSourceOptions())));
        append(canonical, raw(String.valueOf(request.indexedWebSources())));
        append(canonical, sourcePolicy == null ? "" : sourcePolicy.effectiveScope().name());
        append(canonical, sourcePolicy == null ? "" : sourcePolicy.fingerprint());
        append(canonical, RAG_PROMPT_CONTRACT_VERSION);
        append(canonical, RAG_VALIDATOR_VERSION);
        return new RagAnswerCacheKey(sha256(canonical.toString()));
    }

    private static String retrievalOptions(ChatRagRetrievalOptionsDto options) {
        if (options == null) {
            return "";
        }
        return String.join("|",
                raw(String.valueOf(options.structureTopK())),
                raw(String.valueOf(options.ideaBlockTopK())),
                raw(String.valueOf(options.finalTopK())),
                raw(String.valueOf(options.minScore())),
                raw(String.valueOf(options.dedupe())),
                raw(String.valueOf(options.distilledScoreBoost())),
                raw(String.valueOf(options.queryExpansionEnabled())));
    }

    private static String normalizeQuestion(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String raw(String value) {
        return value == null ? "" : value;
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
