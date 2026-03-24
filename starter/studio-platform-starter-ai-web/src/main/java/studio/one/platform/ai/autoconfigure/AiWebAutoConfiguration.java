package studio.one.platform.ai.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.prompt.PromptManager;
import studio.one.platform.ai.web.controller.AiInfoController;
import studio.one.platform.ai.web.controller.ChatController;
import studio.one.platform.ai.web.controller.EmbeddingController;
import studio.one.platform.ai.web.controller.QueryRewriteController;
import studio.one.platform.ai.web.controller.RagController;
import studio.one.platform.ai.web.controller.VectorController;
import studio.one.platform.constant.PropertyKeys;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ChatPort.class)
@ConditionalOnProperty(prefix = PropertyKeys.AI.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
public class AiWebAutoConfiguration {

    @Bean
    ChatController chatController(ChatPort chatPort, RagPipelineService ragPipelineService) {
        return new ChatController(chatPort, ragPipelineService);
    }

    @Bean
    EmbeddingController embeddingController(EmbeddingPort embeddingPort) {
        return new EmbeddingController(embeddingPort);
    }

    @Bean
    VectorController vectorController(
            EmbeddingPort embeddingPort,
            @Nullable VectorStorePort vectorStorePort,
            ObjectProvider<PromptManager> promptManagerProvider) {
        return new VectorController(embeddingPort, vectorStorePort, promptManagerProvider);
    }

    @Bean
    RagController ragController(RagPipelineService ragPipelineService) {
        return new RagController(ragPipelineService);
    }

    @Bean
    QueryRewriteController queryRewriteController(PromptManager promptManager, ChatPort chatPort) {
        return new QueryRewriteController(promptManager, chatPort);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = PropertyKeys.AI.Endpoints.PREFIX,
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false)
    AiInfoController aiInfoController(
            AiAdapterProperties properties,
            Environment environment,
            @Nullable VectorStorePort vectorStorePort) {
        return new AiInfoController(properties, environment, vectorStorePort);
    }
}
