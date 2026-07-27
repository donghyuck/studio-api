package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;

class LlmBlockifyGeneratorTest {

    @Test
    void callsChatPortAndParsesIdeaBlocks() {
        CapturingChatPort chatPort = new CapturingChatPort("""
                [
                  {
                    "title": "연차휴가",
                    "question": "연차휴가는 어떻게 신청해야 하는가?",
                    "answer": "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 한다.",
                    "keywords": ["연차휴가", "사전 신청"],
                    "tags": ["휴가"],
                    "sourceEvidence": [
                      {
                        "text": "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 한다.",
                        "normalizedBlockIndex": 1,
                        "page": 7
                      }
                    ],
                    "confidence": 0.91
                  }
                ]
                """);
        AiProviderRegistry registry = new AiProviderRegistry(
                "google-ai-gemini",
                Map.of("google-ai-gemini", chatPort),
                Map.of());
        LlmBlockifyGenerator generator = new LlmBlockifyGenerator(registry);

        List<BlockifyBlock> blocks = generator.generate(new BlockifyGenerationRequest(
                "doc-1",
                "section-1",
                "취업규칙 > 휴가",
                List.of(
                        NormalizedBlock.builder(NormalizedBlockType.HEADING, "연차휴가").order(0).build(),
                        NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                        "사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 한다.")
                                .order(1)
                                .build()),
                "blockify-v1",
                "google-ai-gemini",
                "gemini-2.5-flash",
                "gemini-2.5-flash",
                0.0d,
                1.0d,
                true,
                5));

        assertThat(chatPort.request.model()).isEqualTo("gemini-2.5-flash");
        assertThat(chatPort.request.temperature()).isEqualTo(0.0d);
        assertThat(chatPort.request.topP()).isEqualTo(1.0d);
        assertThat(chatPort.request.messages()).extracting(ChatMessage::content)
                .anySatisfy(content -> assertThat(content)
                        .contains("SOURCE TEXT:", "사전에 신청하여 승인을 받아야 한다"));
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).question()).isEqualTo("연차휴가는 어떻게 신청해야 하는가?");
        assertThat(blocks.get(0).keywords()).containsExactly("연차휴가", "사전 신청");
        assertThat(blocks.get(0).sourceEvidence()).hasSize(1);
        assertThat(blocks.get(0).sourceEvidence().get(0).text())
                .isEqualTo("사원은 연차휴가를 사용하려는 경우 사전에 신청하여 승인을 받아야 한다.");
        assertThat(blocks.get(0).sourceEvidence().get(0).normalizedBlockIndex()).isEqualTo(1);
        assertThat(blocks.get(0).sourceEvidence().get(0).page()).isEqualTo(7);
    }

    @Test
    void extractsJsonFromNarrativeResponseAndSupportsAliasFields() {
        CapturingChatPort chatPort = new CapturingChatPort("""
                아래 JSON을 사용하세요.
                ```json
                {
                  "knowledge_blocks": [
                    {
                      "sectionTitle": "병가",
                      "user_question": "병가는 어떤 조건에서 사용할 수 있나요?",
                      "response": "회사는 사원이 업무 외 질병 또는 부상 등으로 병가를 신청하는 경우 연간 30일을 초과하지 않는 범위에서 병가를 허가할 수 있습니다.",
                      "search_keywords": ["병가", "질병", "부상"],
                      "evidence": [
                        {
                          "quote": "회사는 사원이 업무 외 질병•부상 등으로 병가를 신청하는 경우에는 연간 30일을 초과하지 않는 범위 내에서 병가를 허가할 수 있다"
                        }
                      ]
                    }
                  ]
                }
                ```
                """);
        AiProviderRegistry registry = new AiProviderRegistry(
                "google-ai-gemini",
                Map.of("google-ai-gemini", chatPort),
                Map.of());
        LlmBlockifyGenerator generator = new LlmBlockifyGenerator(registry);

        List<BlockifyBlock> blocks = generator.generate(new BlockifyGenerationRequest(
                "doc-1",
                "section-병가",
                "병가",
                List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "회사는 사원이 업무 외 질병•부상 등으로 병가를 신청하는 경우에는 연간 30일을 초과하지 않는 범위 내에서 병가를 허가할 수 있다")
                        .order(1)
                        .build()),
                "blockify-v1",
                "google-ai-gemini",
                "gemini-2.5-flash",
                "gemini-2.5-flash",
                0.0d,
                1.0d,
                true,
                5));

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).title()).isEqualTo("병가");
        assertThat(blocks.get(0).question()).contains("병가");
        assertThat(blocks.get(0).answer()).contains("연간 30일");
        assertThat(blocks.get(0).keywords()).containsExactly("병가", "질병", "부상");
        assertThat(blocks.get(0).sourceEvidence()).hasSize(1);
        assertThat(blocks.get(0).sourceEvidence().get(0).text()).contains("업무 외 질병");
    }

    private static final class CapturingChatPort implements ChatPort {
        private final String response;
        private ChatRequest request;

        private CapturingChatPort(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.request = request;
            return new ChatResponse(List.of(ChatMessage.assistant(response)), request.model(), Map.of());
        }
    }
}
