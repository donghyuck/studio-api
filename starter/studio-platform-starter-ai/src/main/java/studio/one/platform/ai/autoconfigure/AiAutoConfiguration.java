package studio.one.platform.ai.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import studio.one.platform.ai.autoconfigure.config.AiProviderRegistryConfiguration;
import studio.one.platform.ai.autoconfigure.config.RagPipelineConfiguration;
import studio.one.platform.ai.autoconfigure.config.LangChainChatConfiguration;
import studio.one.platform.ai.autoconfigure.config.LangChainEmbeddingConfiguration;
import studio.one.platform.ai.autoconfigure.config.PromptConfiguration;
import studio.one.platform.ai.autoconfigure.config.VectorStoreConfiguration;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.web.controller.AiInfoController;
import studio.one.platform.ai.web.controller.ChatController;
import studio.one.platform.ai.web.controller.EmbeddingController;
import studio.one.platform.ai.web.controller.VectorController;
import studio.one.platform.constant.PropertyKeys;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ChatPort.class)
@ConditionalOnProperty(prefix = PropertyKeys.AI.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
@Import({
                
                LangChainEmbeddingConfiguration.class,
                LangChainChatConfiguration.class, 
                AiProviderRegistryConfiguration.class,
                RagPipelineConfiguration.class,
                VectorStoreConfiguration.class,
                PromptConfiguration.class
})
@ComponentScan(basePackageClasses = { ChatController.class, EmbeddingController.class, VectorController.class, AiInfoController.class })
public class AiAutoConfiguration {

}
