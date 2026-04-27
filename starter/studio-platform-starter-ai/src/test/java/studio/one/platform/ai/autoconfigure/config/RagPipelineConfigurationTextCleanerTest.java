package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.service.prompt.PromptRenderer;

class RagPipelineConfigurationTextCleanerTest {

    @Test
    void textCleanerVerifiesPromptExistsBeforeCreatingBean() {
        RagPipelineConfiguration configuration = new RagPipelineConfiguration(provider(null));
        PromptRenderer promptRenderer = mock(PromptRenderer.class);
        ChatPort chatPort = mock(ChatPort.class);
        RagPipelineProperties properties = new RagPipelineProperties();
        properties.getCleaner().setEnabled(true);
        properties.getCleaner().setPrompt("missing-cleaner");

        when(promptRenderer.getRawPrompt("missing-cleaner")).thenThrow(new IllegalArgumentException("missing"));

        assertThatThrownBy(() -> configuration.textCleaner(
                promptRenderer,
                chatPort,
                provider(new ObjectMapper()),
                properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) throws BeansException {
                return value;
            }

            @Override
            public T getIfAvailable() throws BeansException {
                return value;
            }

            @Override
            public T getIfUnique() throws BeansException {
                return value;
            }

            @Override
            public T getObject() throws BeansException {
                return value;
            }
        };
    }
}
