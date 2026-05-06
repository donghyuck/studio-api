package studio.one.application.wiki.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import studio.one.application.wiki.service.WikiPageService;
import studio.one.application.wiki.web.controller.WikiController;
import studio.one.application.wiki.web.controller.WikiMgmtController;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.identity.PrincipalResolver;

@AutoConfiguration(after = WikiAutoConfiguration.class)
@AutoConfigureAfter(WikiAutoConfiguration.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".wiki.web", name = "enabled", havingValue = "true")
@ConditionalOnBean(WikiPageService.class)
public class WikiWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WikiController wikiController(
            WikiPageService wikiPageService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        return new WikiController(wikiPageService, principalResolverProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    WikiMgmtController wikiMgmtController(
            WikiPageService wikiPageService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        return new WikiMgmtController(wikiPageService, principalResolverProvider);
    }
}
