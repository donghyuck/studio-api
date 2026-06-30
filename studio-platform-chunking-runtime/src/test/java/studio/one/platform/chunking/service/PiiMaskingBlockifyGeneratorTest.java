package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;

class PiiMaskingBlockifyGeneratorTest {

    @Test
    void masksSourceTextBeforeDelegateAndDeanonymizesGeneratedBlocks() {
        CapturingGenerator delegate = new CapturingGenerator();
        FakeMaskingPort masking = new FakeMaskingPort();
        PiiMaskingBlockifyGenerator generator = new PiiMaskingBlockifyGenerator(
                delegate, masking, properties());

        List<BlockifyBlock> blocks = generator.generate(request(true));

        String delegatedText = delegate.request.blocks().get(0).text();
        assertThat(delegatedText)
                .contains("<PII_0_EMAIL_ADDRESS>", "<PII_1_PHONE_NUMBER>")
                .doesNotContain("gd@example.com", "010-1234-5678");
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).answer()).contains("gd@example.com", "010-1234-5678");
        assertThat(blocks.get(0).sourceEvidence().get(0).text()).contains("gd@example.com");
    }

    @Test
    void bypassesMaskingWhenRequestDisablesIt() {
        CapturingGenerator delegate = new CapturingGenerator();
        PiiMaskingBlockifyGenerator generator = new PiiMaskingBlockifyGenerator(
                delegate, new FakeMaskingPort(), properties());

        generator.generate(request(false));

        assertThat(delegate.request.blocks().get(0).text()).contains("gd@example.com");
    }

    @Test
    void throwsFailPipelineExceptionWhenRequiredMaskingFails() {
        ChunkingProperties.BlockifyPiiMaskingProperties properties = properties();
        properties.setRequired(true);
        PiiMaskingBlockifyGenerator generator = new PiiMaskingBlockifyGenerator(
                request -> List.of(),
                new FailingMaskingPort(),
                properties);

        assertThatThrownBy(() -> generator.generate(request(true)))
                .isInstanceOf(BlockifyPiiMaskingException.class)
                .satisfies(error -> assertThat(((BlockifyPiiMaskingException) error).failPipeline()).isTrue());
    }

    private BlockifyGenerationRequest request(Boolean piiMaskingEnabled) {
        return new BlockifyGenerationRequest(
                "doc-1",
                "section-1",
                "연락처",
                List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH,
                                "담당자 이메일은 gd@example.com 이고 전화번호는 010-1234-5678 입니다.")
                        .order(1)
                        .build()),
                "blockify-v1",
                "google-ai-gemini",
                "gemini-2.5-flash",
                "gemini-2.5-flash",
                0.0d,
                1.0d,
                piiMaskingEnabled,
                5);
    }

    private ChunkingProperties.BlockifyPiiMaskingProperties properties() {
        return new ChunkingProperties.BlockifyPiiMaskingProperties();
    }

    private static final class CapturingGenerator implements BlockifyGenerator {
        private BlockifyGenerationRequest request;

        @Override
        public List<BlockifyBlock> generate(BlockifyGenerationRequest request) {
            this.request = request;
            return List.of(new BlockifyBlock(
                    "담당자 연락처",
                    "담당자 연락처는 무엇인가?",
                    "담당자 이메일은 <PII_0_EMAIL_ADDRESS> 이고 전화번호는 <PII_1_PHONE_NUMBER> 입니다.",
                    List.of("<PII_0_EMAIL_ADDRESS>"),
                    List.of("연락처"),
                    List.of(new BlockifySourceEvidence(
                            "담당자 이메일은 <PII_0_EMAIL_ADDRESS> 이고 전화번호는 <PII_1_PHONE_NUMBER> 입니다.",
                            1, null, null, null, null, List.of("연락처"), "section-1", List.of("p1"))),
                    0.9d));
        }
    }

    private static final class FakeMaskingPort implements BlockifyPiiMaskingPort {
        @Override
        public MaskedRequest mask(BlockifyGenerationRequest request) {
            Map<String, String> replacements = new LinkedHashMap<>();
            replacements.put("<PII_0_EMAIL_ADDRESS>", "gd@example.com");
            replacements.put("<PII_1_PHONE_NUMBER>", "010-1234-5678");
            NormalizedBlock block = request.blocks().get(0);
            NormalizedBlock masked = NormalizedBlock.builder(block.type(),
                            block.text()
                                    .replace("gd@example.com", "<PII_0_EMAIL_ADDRESS>")
                                    .replace("010-1234-5678", "<PII_1_PHONE_NUMBER>"))
                    .order(block.order())
                    .build();
            BlockifyGenerationRequest maskedRequest = new BlockifyGenerationRequest(
                    request.sourceDocumentId(), request.sectionId(), request.headingPath(), List.of(masked),
                    request.promptVersion(), request.llmProvider(), request.llmModel(), request.generatorModel(),
                    request.temperature(), request.topP(), request.piiMaskingEnabled(), request.maxBlocksPerSection());
            return new MaskedRequest(maskedRequest, replacements, replacements.size(),
                    Set.of("EMAIL_ADDRESS", "PHONE_NUMBER"));
        }

        @Override
        public String deanonymize(String text, MaskedRequest maskedRequest) {
            if (text == null) {
                return null;
            }
            String restored = text;
            for (Map.Entry<String, String> entry : maskedRequest.replacements().entrySet()) {
                restored = restored.replace(entry.getKey(), entry.getValue());
            }
            return restored;
        }
    }

    private static final class FailingMaskingPort implements BlockifyPiiMaskingPort {
        @Override
        public MaskedRequest mask(BlockifyGenerationRequest request) {
            throw new IllegalStateException("presidio unavailable");
        }

        @Override
        public String deanonymize(String text, MaskedRequest maskedRequest) {
            return text;
        }
    }
}
