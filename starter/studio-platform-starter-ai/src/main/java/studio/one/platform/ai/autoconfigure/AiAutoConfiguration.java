package studio.one.platform.ai.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import studio.one.platform.ai.autoconfigure.config.AiProviderRegistryConfiguration;
import studio.one.platform.ai.autoconfigure.config.GoogleGenAiChatPortFactoryConfiguration;
import studio.one.platform.ai.autoconfigure.config.GoogleGenAiEmbeddingPortFactoryConfiguration;
import studio.one.platform.ai.autoconfigure.config.KeywordExtractorConfiguration;
import studio.one.platform.ai.autoconfigure.config.OllamaPortFactoryConfiguration;
import studio.one.platform.ai.autoconfigure.config.OpenAiPortFactoryConfiguration;
import studio.one.platform.ai.autoconfigure.config.PromptConfiguration;
import studio.one.platform.ai.autoconfigure.config.ProviderChatConfiguration;
import studio.one.platform.ai.autoconfigure.config.ProviderEmbeddingConfiguration;
import studio.one.platform.ai.autoconfigure.config.RagPipelineConfiguration;
import studio.one.platform.ai.autoconfigure.config.VectorStoreConfiguration;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.constant.PropertyKeys;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(name = "studio.one.platform.chunking.autoconfigure.ChunkingAutoConfiguration")
@ConditionalOnClass(ChatPort.class)
@ConditionalOnProperty(prefix = PropertyKeys.AI.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
@Import({
                OpenAiPortFactoryConfiguration.class,
                GoogleGenAiChatPortFactoryConfiguration.class,
                GoogleGenAiEmbeddingPortFactoryConfiguration.class,
                OllamaPortFactoryConfiguration.class,
                ProviderEmbeddingConfiguration.class,
                ProviderChatConfiguration.class,
                AiProviderRegistryConfiguration.class,
                RagPipelineConfiguration.class,
                VectorStoreConfiguration.class,
                PromptConfiguration.class,
                KeywordExtractorConfiguration.class
})
public class AiAutoConfiguration {

}
