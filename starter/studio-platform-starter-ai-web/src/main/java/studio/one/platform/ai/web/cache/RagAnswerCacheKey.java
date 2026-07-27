package studio.one.platform.ai.web.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRagRetrievalOptionsDto;

public record RagAnswerCacheKey(String digest) {

    private static final String SCHEMA_VERSION = "v2";
    private static final String RAG_PROMPT_CONTRACT_VERSION = "rag-grounding-v2";

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
        append(canonical, RAG_PROMPT_CONTRACT_VERSION);
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
