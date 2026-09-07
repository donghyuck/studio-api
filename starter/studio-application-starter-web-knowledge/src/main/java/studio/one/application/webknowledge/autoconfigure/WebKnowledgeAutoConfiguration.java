package studio.one.application.webknowledge.autoconfigure;

import java.util.concurrent.Executor;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import studio.one.application.webknowledge.application.DefaultWebKnowledgeSourceService;
import studio.one.application.webknowledge.application.WebCrawlPolicyResolver;
import studio.one.application.webknowledge.application.WebCrawlUrlPolicy;
import studio.one.application.webknowledge.application.WebKnowledgeIndexedRagSourceProvider;
import studio.one.application.webknowledge.application.WebKnowledgeTeamKnowledgeSourceContributor;
import studio.one.application.webknowledge.application.WebKnowledgeContentSanitizer;
import studio.one.application.webknowledge.application.WebKnowledgeRagIndexJobSourceExecutor;
import studio.one.application.webknowledge.application.WebKnowledgeRagObjectAuthorizer;
import studio.one.application.webknowledge.application.WebKnowledgeSiteCrawlCoordinator;
import studio.one.application.webknowledge.application.WebKnowledgeSitePreviewService;
import studio.one.application.webknowledge.application.WebKnowledgeQuotaService;
import studio.one.application.webknowledge.application.WebKnowledgeSourceService;
import studio.one.application.webknowledge.application.WebKnowledgeStatePersistence;
import studio.one.application.webknowledge.application.WebKnowledgeCrawlStatePersistence;
import studio.one.application.webknowledge.application.WebPageFetchPolicy;
import studio.one.application.webknowledge.application.WebPageFetchPort;
import studio.one.application.webknowledge.application.WebPageMetadataExtractor;
import studio.one.application.webknowledge.application.WebSiteDiscoveryPort;
import studio.one.application.webknowledge.application.WebSitemapParser;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCorpusPageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCorpusRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlItemJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeQuotaUsageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.application.webknowledge.infrastructure.web.JsoupWebSiteDiscovery;
import studio.one.application.webknowledge.infrastructure.web.SafeHttpWebPageFetcher;
import studio.one.application.webknowledge.web.WebKnowledgeSourceController;
import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceCapabilities;
import studio.one.platform.ai.service.pipeline.RagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceExecutor;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.chunking.artifact.ChunkSetStore;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.textract.infrastructure.extractor.impl.HtmlFileParser;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@AutoConfigureAfter(name = {
        "studio.one.platform.ai.autoconfigure.AiAutoConfiguration",
        "studio.one.platform.ai.autoconfigure.config.RagPipelineConfiguration",
        "studio.one.platform.chunking.autoconfigure.ChunkingAutoConfiguration",
        "studio.one.platform.security.autoconfigure.SecurityAutoConfiguration",
        "studio.one.platform.workspace.autoconfigure.WorkspaceAutoConfiguration" })
@EnableConfigurationProperties(IndexedWebProperties.class)
@ConditionalOnProperty(prefix = "studio.ai.indexed-web", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebKnowledgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "webKnowledgeTaskExecutor")
    TaskExecutor webKnowledgeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("web-knowledge-");
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    WebPageFetchPolicy webPageFetchPolicy(IndexedWebProperties properties) {
        var fetch = properties.getFetch();
        return new WebPageFetchPolicy(
                fetch.getConnectTimeout(),
                fetch.getRequestTimeout(),
                fetch.getMaxResponseBytes(),
                fetch.getMaxNormalizedChars(),
                fetch.getMaxRedirects(),
                fetch.isRobotsEnabled(),
                fetch.getUserAgent());
    }

    @Bean
    @ConditionalOnMissingBean
    WebPageFetchPort webPageFetchPort(WebPageFetchPolicy policy) {
        return new SafeHttpWebPageFetcher(policy);
    }

    @Bean
    @ConditionalOnMissingBean
    WebPageMetadataExtractor webPageMetadataExtractor() {
        return new WebPageMetadataExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    WebSiteDiscoveryPort webSiteDiscoveryPort() {
        return new JsoupWebSiteDiscovery();
    }

    @Bean
    @ConditionalOnMissingBean
    WebSitemapParser webSitemapParser() {
        return new WebSitemapParser();
    }

    @Bean
    @ConditionalOnMissingBean
    WebCrawlUrlPolicy webCrawlUrlPolicy() {
        return new WebCrawlUrlPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    WebKnowledgeSitePreviewService webKnowledgeSitePreviewService(
            WebPageFetchPort fetchPort,
            WebSiteDiscoveryPort discovery,
            WebSitemapParser sitemapParser,
            WebCrawlUrlPolicy urlPolicy,
            WebCrawlPolicyResolver policyResolver,
            IndexedWebProperties properties) {
        return new WebKnowledgeSitePreviewService(
                fetchPort,
                discovery,
                sitemapParser,
                urlPolicy,
                policyResolver,
                properties.getCrawl().isSiteCrawlEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    WebCrawlPolicyResolver webCrawlPolicyResolver(IndexedWebProperties properties) {
        var crawl = properties.getCrawl();
        return new WebCrawlPolicyResolver(new WebCrawlPolicyResolver.Limits(
                crawl.getDefaultMaxDepth(),
                crawl.getMaximumDepth(),
                crawl.getDefaultMaxPages(),
                crawl.getMaximumPages(),
                crawl.getDefaultMaxConcurrency(),
                crawl.getMaximumConcurrency(),
                crawl.getMinDelayPerOrigin(),
                crawl.getMaxTotalResponseBytes(),
                crawl.getMaxTotalNormalizedChars(),
                crawl.getMaxRunDuration()));
    }

    @Bean
    @ConditionalOnMissingBean
    WebKnowledgeContentSanitizer webKnowledgeContentSanitizer(IndexedWebProperties properties) {
        return new WebKnowledgeContentSanitizer(
                properties.getContentSecurity().isPiiRedactionEnabled());
    }

    @Bean
    @ConditionalOnBean({
            WebKnowledgeSourceJpaRepository.class,
            WebKnowledgeRevisionJpaRepository.class })
    @ConditionalOnMissingBean
    WebKnowledgeStatePersistence webKnowledgeStatePersistence(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            EntityManagerFactory entityManagerFactory) {
        return new WebKnowledgeStatePersistence(sources, revisions, entityManagerFactory);
    }

    @Bean
    @ConditionalOnBean({
            WebKnowledgeSourceJpaRepository.class,
            WebKnowledgeCrawlRunJpaRepository.class })
    @ConditionalOnMissingBean
    WebKnowledgeCrawlStatePersistence webKnowledgeCrawlStatePersistence(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeCrawlRunJpaRepository runs,
            EntityManagerFactory entityManagerFactory) {
        return new WebKnowledgeCrawlStatePersistence(sources, runs, entityManagerFactory);
    }

    @Bean
    @ConditionalOnBean({
            WebKnowledgeSourceJpaRepository.class,
            WebKnowledgeRevisionJpaRepository.class,
            WebKnowledgeStatePersistence.class,
            RagPipelineService.class,
            RagEmbeddingProfileResolver.class,
            ChunkingOrchestrator.class,
            RagChunkStageStore.class,
            ChunkSetStore.class })
    @ConditionalOnMissingBean(name = "webKnowledgeRagIndexJobSourceExecutor")
    RagIndexJobSourceExecutor webKnowledgeRagIndexJobSourceExecutor(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            WebKnowledgeStatePersistence statePersistence,
            WebPageFetchPort fetchPort,
            WebPageMetadataExtractor metadataExtractor,
            ChunkingOrchestrator chunking,
            RagChunkStageStore stageStore,
            ChunkSetStore chunkSetStore,
            RagPipelineService ragPipeline,
            RagEmbeddingProfileResolver embeddingResolver,
            WebPageFetchPolicy fetchPolicy,
            WebKnowledgeContentSanitizer contentSanitizer,
            ObjectMapper objectMapper) {
        return new WebKnowledgeRagIndexJobSourceExecutor(
                sources,
                revisions,
                statePersistence,
                fetchPort,
                metadataExtractor,
                new HtmlFileParser(),
                new TextractNormalizedDocumentAdapter(),
                chunking,
                stageStore,
                chunkSetStore,
                ragPipeline,
                embeddingResolver,
                fetchPolicy,
                contentSanitizer,
                objectMapper);
    }

    @Bean
    @ConditionalOnBean({
            WebKnowledgeSourceJpaRepository.class,
            WebKnowledgeCrawlRunJpaRepository.class,
            WebKnowledgeCrawlItemJpaRepository.class,
            WebKnowledgePageJpaRepository.class,
            WebKnowledgePageRevisionJpaRepository.class,
            WebKnowledgeCorpusRevisionJpaRepository.class,
            WebKnowledgeCorpusPageJpaRepository.class,
            RagPipelineService.class,
            RagEmbeddingProfileResolver.class,
            ChunkingOrchestrator.class,
            RagChunkStageStore.class,
            ChunkSetStore.class })
    @ConditionalOnMissingBean
    WebKnowledgeSiteCrawlCoordinator webKnowledgeSiteCrawlCoordinator(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeCrawlRunJpaRepository runs,
            WebKnowledgeCrawlItemJpaRepository items,
            WebKnowledgePageJpaRepository pages,
            WebKnowledgePageRevisionJpaRepository pageRevisions,
            WebKnowledgeCorpusRevisionJpaRepository corpusRevisions,
            WebKnowledgeCorpusPageJpaRepository corpusPages,
            WebPageFetchPort fetchPort,
            WebSiteDiscoveryPort discovery,
            WebSitemapParser sitemapParser,
            WebCrawlUrlPolicy urlPolicy,
            WebPageMetadataExtractor metadataExtractor,
            ChunkingOrchestrator chunking,
            RagChunkStageStore stageStore,
            ChunkSetStore chunkSetStore,
            RagPipelineService ragPipeline,
            RagEmbeddingProfileResolver embeddingResolver,
            WebPageFetchPolicy fetchPolicy,
            WebKnowledgeContentSanitizer contentSanitizer,
            WebKnowledgeQuotaService quotaService,
            WebKnowledgeCrawlStatePersistence statePersistence,
            ObjectMapper objectMapper,
            IndexedWebProperties properties) {
        var crawl = properties.getCrawl();
        return new WebKnowledgeSiteCrawlCoordinator(
                sources,
                runs,
                items,
                pages,
                pageRevisions,
                corpusRevisions,
                corpusPages,
                fetchPort,
                discovery,
                sitemapParser,
                urlPolicy,
                metadataExtractor,
                new HtmlFileParser(),
                new TextractNormalizedDocumentAdapter(),
                chunking,
                stageStore,
                chunkSetStore,
                ragPipeline,
                embeddingResolver,
                fetchPolicy,
                contentSanitizer,
                quotaService,
                statePersistence,
                objectMapper,
                new WebKnowledgeSiteCrawlCoordinator.Limits(
                        crawl.getMaxActiveRunsGlobal(),
                        crawl.getMaxActiveRunsPerWorkspace(),
                        crawl.getMaxActiveRunsPerPrincipal()));
    }

    @Bean
    @ConditionalOnBean({
            WebKnowledgeQuotaUsageJpaRepository.class,
            WebKnowledgeSourceJpaRepository.class,
            WebKnowledgePageJpaRepository.class,
            WebKnowledgePageRevisionJpaRepository.class,
            WebKnowledgeCrawlRunJpaRepository.class })
    @ConditionalOnMissingBean
    WebKnowledgeQuotaService webKnowledgeQuotaService(
            WebKnowledgeQuotaUsageJpaRepository quotas,
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgePageJpaRepository pages,
            WebKnowledgePageRevisionJpaRepository pageRevisions,
            WebKnowledgeCrawlRunJpaRepository runs,
            IndexedWebProperties properties) {
        var quota = properties.getQuota();
        return new WebKnowledgeQuotaService(
                quotas,
                sources,
                pages,
                pageRevisions,
                runs,
                new WebKnowledgeQuotaService.Limits(
                        quota.getMaxSourcesPerWorkspace(),
                        quota.getMaxActivePagesPerWorkspace(),
                        quota.getMaxSnapshotUnitsPerWorkspace()));
    }

    @Bean
    @ConditionalOnBean(WebKnowledgeSiteCrawlCoordinator.class)
    @ConditionalOnMissingBean(name = "webKnowledgeCrawlRecoveryRunner")
    ApplicationRunner webKnowledgeCrawlRecoveryRunner(
            WebKnowledgeSiteCrawlCoordinator coordinator,
            TaskExecutor webKnowledgeTaskExecutor,
            IndexedWebProperties properties) {
        return arguments -> {
            if (!properties.getCrawl().isSiteCrawlEnabled()) {
                return;
            }
            coordinator.resumableRunIds(java.time.Instant.now())
                    .forEach(runId -> webKnowledgeTaskExecutor.execute(() -> coordinator.execute(runId)));
        };
    }

    @Bean
    @ConditionalOnBean({
            WebKnowledgeSourceJpaRepository.class,
            WebKnowledgeRevisionJpaRepository.class,
            RagIndexJobService.class,
            RagPipelineService.class })
    @ConditionalOnMissingBean
    WebKnowledgeSourceService webKnowledgeSourceService(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            RagIndexJobService jobs,
            RagPipelineService ragPipeline,
            RagEmbeddingProfileResolver embeddingResolver,
            WebKnowledgeContentSanitizer contentSanitizer,
            TaskExecutor webKnowledgeTaskExecutor,
            WebCrawlPolicyResolver crawlPolicyResolver,
            WebKnowledgeSiteCrawlCoordinator siteCrawlCoordinator,
            WebKnowledgeQuotaService quotaService,
            ObjectMapper objectMapper,
            IndexedWebProperties properties) {
        return new DefaultWebKnowledgeSourceService(
                sources,
                revisions,
                jobs,
                ragPipeline,
                embeddingResolver,
                contentSanitizer,
                (Executor) webKnowledgeTaskExecutor,
                crawlPolicyResolver,
                siteCrawlCoordinator,
                quotaService,
                objectMapper,
                properties.getCrawl().isSiteCrawlEnabled());
    }

    @Bean
    @ConditionalOnMissingBean(name = "webKnowledgeIndexedRagSourceProvider")
    IndexedRagSourceProvider webKnowledgeIndexedRagSourceProvider(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            WebKnowledgeCorpusRevisionJpaRepository corpusRevisions,
            WebKnowledgeCorpusPageJpaRepository corpusPages,
            WebKnowledgePageRevisionJpaRepository pageRevisions,
            IndexedWebProperties properties) {
        return new WebKnowledgeIndexedRagSourceProvider(
                sources,
                revisions,
                corpusRevisions,
                corpusPages,
                pageRevisions,
                properties.getMaxSelectedSources(),
                new IndexedRagSourceCapabilities(
                        true,
                        properties.getMaxSelectedSources(),
                        java.util.List.of("https"),
                        2048,
                        properties.getCrawl().isSiteCrawlEnabled()
                                ? java.util.List.of("SINGLE_PAGE", "SITE")
                                : java.util.List.of("SINGLE_PAGE"),
                        properties.getCrawl().isSiteCrawlEnabled(),
                        properties.getCrawl().getDefaultMaxDepth(),
                        properties.getCrawl().getMaximumDepth(),
                        properties.getCrawl().getDefaultMaxPages(),
                        properties.getCrawl().getMaximumPages(),
                        properties.getCrawl().getDefaultMaxConcurrency(),
                        properties.getCrawl().getMaximumConcurrency(),
                        properties.getCrawl().isSiteCrawlEnabled()
                                ? java.util.List.of("SITEMAP_AND_LINKS", "SITEMAP_ONLY", "LINKS_ONLY")
                                : java.util.List.of()));
    }

    @Bean
    @ConditionalOnMissingBean(name = "webKnowledgeTeamKnowledgeSourceContributor")
    TeamKnowledgeSourceContributor webKnowledgeTeamKnowledgeSourceContributor(
            WebKnowledgeSourceJpaRepository sources,
            @Qualifier("webKnowledgeIndexedRagSourceProvider") IndexedRagSourceProvider indexedSources) {
        return new WebKnowledgeTeamKnowledgeSourceContributor(sources, indexedSources);
    }

    @Bean
    @ConditionalOnBean({ PrincipalResolver.class, WorkspacePermissionService.class })
    @ConditionalOnMissingBean(name = "webKnowledgeRagObjectAuthorizer")
    RagObjectAuthorizer webKnowledgeRagObjectAuthorizer(
            WebKnowledgeSourceJpaRepository sources,
            PrincipalResolver principalResolver,
            WorkspacePermissionService permissions) {
        return new WebKnowledgeRagObjectAuthorizer(sources, principalResolver, permissions);
    }

    @Bean
    @ConditionalOnBean({
            WebKnowledgeSourceService.class,
            PrincipalResolver.class,
            WorkspacePermissionService.class })
    @ConditionalOnMissingBean
    WebKnowledgeSourceController webKnowledgeSourceController(
            WebKnowledgeSourceService service,
            PrincipalResolver principalResolver,
            WorkspacePermissionService workspacePermissions,
            WebKnowledgeSitePreviewService previewService) {
        return new WebKnowledgeSourceController(
                service, principalResolver, workspacePermissions, previewService);
    }

    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor webKnowledgeEntityScanRegistrar(Environment environment) {
            return EntityScanRegistrarSupport.entityScanRegistrar(
                    "studio.ai.indexed-web.entity-packages",
                    WebKnowledgeSourceEntity.class.getPackageName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @EnableJpaRepositories(basePackageClasses = {
            WebKnowledgeSourceJpaRepository.class,
            WebKnowledgeRevisionJpaRepository.class,
            WebKnowledgeCrawlRunJpaRepository.class,
            WebKnowledgeCrawlItemJpaRepository.class,
            WebKnowledgePageJpaRepository.class,
            WebKnowledgePageRevisionJpaRepository.class,
            WebKnowledgeCorpusRevisionJpaRepository.class,
            WebKnowledgeCorpusPageJpaRepository.class,
            WebKnowledgeQuotaUsageJpaRepository.class })
    static class JpaConfig {
    }
}
