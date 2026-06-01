package studio.one.platform.ai.autoconfigure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.web.controller.AiProviderMgmtController;
import studio.one.platform.constant.PropertyKeys;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestController.class)
@ConditionalOnBean(AiEmbeddingOptionCatalog.class)
@ConditionalOnProperty(prefix = PropertyKeys.AI.Endpoints.PREFIX, name = "enabled", havingValue = "true")
@Import(AiProviderMgmtController.class)
public class AiProviderEndpointConfiguration {
}
