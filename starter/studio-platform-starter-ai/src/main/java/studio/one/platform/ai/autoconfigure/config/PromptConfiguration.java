package studio.one.platform.ai.autoconfigure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.service.prompt.PromptManager;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration
@EnableConfigurationProperties(PromptProperties.class)
@Slf4j
public class PromptConfiguration {

    @Bean(name = "componnets:ai:prompt-manager")
    public PromptManager promptManager(PromptProperties properties, ResourceLoader resourceLoader, ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider); 
        PromptManager manager = new PromptManager(properties.getPrompts(), resourceLoader);

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(PromptManager.class, true), LogUtils.red(State.CREATED.toString())));

        return manager;
    }

}
