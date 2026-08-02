package studio.one.platform.ai.web.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.ai.web.controller.RagAnswerMode;
import studio.one.platform.ai.web.controller.ResolvedRagAnswerPolicy;

class RagAnswerCacheKeyTest {

    @Test
    void keyChangesForAuthorizationScopeObjectAndEvidence() {
        ChatRagRequestDto request = request("attachment", "11");

        RagAnswerCacheKey base = RagAnswerCacheKey.create(
                "principal:user-a", request, "Question", "chat-default", "evidence-a");

        assertThat(RagAnswerCacheKey.create(
                "principal:user-b", request, "Question", "chat-default", "evidence-a")).isNotEqualTo(base);
        assertThat(RagAnswerCacheKey.create(
                "principal:user-a", request("attachment", "12"), "Question", "chat-default", "evidence-a"))
                .isNotEqualTo(base);
        assertThat(RagAnswerCacheKey.create(
                "principal:user-a", request, "Question", "chat-default", "evidence-b")).isNotEqualTo(base);
    }

    @Test
    void revisionChangeInPackedEvidenceFingerprintProducesMiss() {
        ChatRagRequestDto request = request("attachment", "11");

        RagAnswerCacheKey revisionOne = RagAnswerCacheKey.create(
                "principal:user-a", request, "Question", "chat-default", "revision-1-evidence");
        RagAnswerCacheKey revisionTwo = RagAnswerCacheKey.create(
                "principal:user-a", request, "Question", "chat-default", "revision-2-evidence");

        assertThat(revisionTwo).isNotEqualTo(revisionOne);
    }

    @Test
    void whitespaceNormalizedQuestionProducesStableKeyWithoutLosingCase() {
        ChatRagRequestDto request = request("attachment", "11");

        assertThat(RagAnswerCacheKey.create(
                "principal:user-a", request, "Why  War?", "chat-default", "evidence-a"))
                .isEqualTo(RagAnswerCacheKey.create(
                        "principal:user-a", request, "  Why War?  ", "chat-default", "evidence-a"))
                .isNotEqualTo(RagAnswerCacheKey.create(
                        "principal:user-a", request, "why war?", "chat-default", "evidence-a"));
    }

    @Test
    void effectiveModeAndPolicyFingerprintIsolateCacheEntries() {
        ChatRagRequestDto request = request("attachment", "11");

        RagAnswerCacheKey strict = RagAnswerCacheKey.create(
                "principal:user-a", request, "Question", "chat-default", "evidence-a",
                policy(RagAnswerMode.STRICT_GROUNDED, "policy-a"));
        RagAnswerCacheKey inference = RagAnswerCacheKey.create(
                "principal:user-a", request, "Question", "chat-default", "evidence-a",
                policy(RagAnswerMode.GROUNDED_INFERENCE, "policy-a"));
        RagAnswerCacheKey changedPolicy = RagAnswerCacheKey.create(
                "principal:user-a", request, "Question", "chat-default", "evidence-a",
                policy(RagAnswerMode.STRICT_GROUNDED, "policy-b"));

        assertThat(strict).isNotEqualTo(inference).isNotEqualTo(changedPolicy);
    }

    private ResolvedRagAnswerPolicy policy(RagAnswerMode mode, String fingerprint) {
        return new ResolvedRagAnswerPolicy(
                mode,
                mode,
                ResolvedRagAnswerPolicy.Source.REQUEST,
                false,
                ResolvedRagAnswerPolicy.ReasonCode.NONE,
                "v1",
                fingerprint);
    }

    private ChatRagRequestDto request(String objectType, String objectId) {
        return new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "question")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "question",
                3,
                objectType,
                objectId);
    }
}
