package studio.one.platform.ai.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import studio.one.platform.ai.autoconfigure.config.AiProviderRegistryConfiguration;
import studio.one.platform.ai.autoconfigure.config.LangChainChatConfiguration;
import studio.one.platform.ai.autoconfigure.config.LangChainEmbeddingConfiguration;
import studio.one.platform.ai.autoconfigure.config.VectorStoreConfiguration;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.service.config.AiServiceConfiguration;
import studio.one.platform.ai.web.controller.ChatController;
import studio.one.platform.ai.web.controller.EmbeddingController;
import studio.one.platform.ai.web.controller.VectorController;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ChatPort.class)
@Import({
        AiServiceConfiguration.class,
        LangChainEmbeddingConfiguration.class,
        LangChainChatConfiguration.class,
        VectorStoreConfiguration.class,
        AiProviderRegistryConfiguration.class
})
@ComponentScan(basePackageClasses = {ChatController.class, EmbeddingController.class, VectorController.class})
public class AiAutoConfiguration {
        
}
