package studio.one.platform.ai.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.web.controller.AiInfoController;
import studio.one.platform.ai.web.controller.ChatController;
import studio.one.platform.ai.web.controller.EmbeddingController;
import studio.one.platform.ai.web.controller.QueryRewriteController;
import studio.one.platform.ai.web.controller.RagController;
import studio.one.platform.ai.web.controller.TokenUsageJsonComponent;
import studio.one.platform.ai.web.controller.VectorController;
import studio.one.platform.constant.PropertyKeys;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ChatPort.class)
@ConditionalOnProperty(prefix = PropertyKeys.AI.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
@ComponentScan(basePackageClasses = {
        ChatController.class,
        EmbeddingController.class,
        VectorController.class,
        RagController.class,
        QueryRewriteController.class,
        AiInfoController.class,
        TokenUsageJsonComponent.class
})
public class AiWebAutoConfiguration {

}
