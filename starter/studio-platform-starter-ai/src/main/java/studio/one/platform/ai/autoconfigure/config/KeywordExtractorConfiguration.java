package studio.one.platform.ai.autoconfigure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.service.keyword.KeywordExtractor;
import studio.one.platform.ai.service.keyword.LlmKeywordExtractor;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(LlmKeywordExtractor.class)
@RequiredArgsConstructor
@Slf4j
public class KeywordExtractorConfiguration {

    private final ObjectProvider<I18n> i18nProvider;

    @Bean
    @ConditionalOnMissingBean(KeywordExtractor.class)
    public KeywordExtractor keywordExtractor(ChatPort chatPort) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(LlmKeywordExtractor.class, true),
                LogUtils.green(ChatPort.class, true),
                LogUtils.red(State.CREATED.toString())));
        return new LlmKeywordExtractor(chatPort);
    }
}
