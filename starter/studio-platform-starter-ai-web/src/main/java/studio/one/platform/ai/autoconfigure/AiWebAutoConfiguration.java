package studio.one.platform.ai.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.core.chat.ChatMemoryStore;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.ai.web.controller.AiWebExceptionHandler;
import studio.one.platform.ai.web.controller.AiInfoController;
import studio.one.platform.ai.web.controller.ChatController;
import studio.one.platform.ai.web.controller.EmbeddingController;
import studio.one.platform.ai.web.controller.InMemoryChatMemoryStore;
import studio.one.platform.ai.web.controller.QueryRewriteController;
import studio.one.platform.ai.web.controller.RagController;
import studio.one.platform.ai.web.controller.RagContextBuilder;
import studio.one.platform.ai.web.controller.VectorController;
import studio.one.platform.constant.PropertyKeys;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ChatPort.class)
@Conditional(AiWebEndpointCondition.class)
@EnableConfigurationProperties({AiWebRagProperties.class, AiWebChatProperties.class})
public class AiWebAutoConfiguration {

    @Bean
    RagContextBuilder ragContextBuilder(AiWebRagProperties properties) {
        return new RagContextBuilder(properties);
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemoryStore.class)
    @ConditionalOnProperty(prefix = PropertyKeys.AI.Endpoints.PREFIX + ".chat.memory", name = "enabled", havingValue = "true")
    ChatMemoryStore chatMemoryStore(AiWebChatProperties properties) {
        return new InMemoryChatMemoryStore(properties.getMemory());
    }

    @Bean
    ChatController chatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            AiWebRagProperties ragProperties,
            AiWebChatProperties chatProperties,
            @Nullable ChatMemoryStore chatMemoryStore) {
        return new ChatController(providerRegistry, ragPipelineService, ragContextBuilder,
                ragProperties.getDiagnostics().isAllowClientDebug(),
                chatMemoryStore,
                chatProperties.getMemory().isEnabled());
    }

    @Bean
    AiWebExceptionHandler aiWebExceptionHandler() {
        return new AiWebExceptionHandler();
    }

    @Bean
    EmbeddingController embeddingController(EmbeddingPort embeddingPort) {
        return new EmbeddingController(embeddingPort);
    }

    @Bean
    VectorController vectorController(
            EmbeddingPort embeddingPort,
            @Nullable VectorStorePort vectorStorePort) {
        return new VectorController(embeddingPort, vectorStorePort);
    }

    @Bean
    RagController ragController(RagPipelineService ragPipelineService) {
        return new RagController(ragPipelineService);
    }

    @Bean
    QueryRewriteController queryRewriteController(PromptRenderer promptManager, ChatPort chatPort) {
        return new QueryRewriteController(promptManager, chatPort);
    }

    @Bean
    AiInfoController aiInfoController(
            AiAdapterProperties properties,
            Environment environment,
            @Nullable VectorStorePort vectorStorePort) {
        return new AiInfoController(properties, environment, vectorStorePort);
    }
}
