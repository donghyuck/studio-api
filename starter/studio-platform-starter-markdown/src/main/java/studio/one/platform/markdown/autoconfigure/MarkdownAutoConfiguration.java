package studio.one.platform.markdown.autoconfigure;

import java.time.Clock;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.service.pipeline.RagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagObjectMetadataContributor;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.artifact.ChunkSetStore;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertJobListener;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertDirectResultStore;
import studio.one.platform.documentconvert.application.service.DocumentConvertService;
import studio.one.platform.markdown.application.MarkdownDocumentService;
import studio.one.platform.markdown.application.port.MarkdownConversionPort;
import studio.one.platform.markdown.application.port.MarkdownNativeExtractorPort;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.application.port.MarkdownPagePreviewPort;
import studio.one.platform.markdown.application.port.MarkdownPipelinePort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.application.port.MarkdownSourcePort;
import studio.one.platform.markdown.application.port.MarkdownTaskExecutor;
import studio.one.platform.markdown.application.port.MarkdownTransactionOperations;
import studio.one.platform.markdown.infrastructure.JdbcMarkdownRepository;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;

@AutoConfiguration(afterName = {
        "studio.one.platform.autoconfigure.persistence.jdbc.JdbcAutoConfiguration",
        "studio.one.application.attachment.autoconfigure.AttachmentAutoConfiguration",
        "studio.one.platform.textract.autoconfigure.TextractAutoConfiguration",
        "studio.one.platform.documentconvert.autoconfigure.DocumentConvertAutoConfiguration"
})
@EnableConfigurationProperties(MarkdownProperties.class)
@ConditionalOnProperty(prefix = "studio.markdown", name = "enabled", havingValue = "true")
public class MarkdownAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(NamedParameterJdbcTemplate.class)
    MarkdownRepository markdownRepository(NamedParameterJdbcTemplate jdbc) {
        return new JdbcMarkdownRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AttachmentService.class)
    MarkdownSourcePort markdownSourcePort(AttachmentService attachmentService, MarkdownProperties properties) {
        return new AttachmentMarkdownSourceAdapter(attachmentService, properties.getMaxSourceBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FileContentExtractionService.class)
    MarkdownNativeExtractorPort markdownNativeExtractorPort(FileContentExtractionService extractionService,
            ObjectMapper objectMapper, MarkdownProperties properties, MarkdownRepository repository,
            ParsedFileNormalizedDocumentMapper normalizer, NormalizedMarkdownRenderer renderer) {
        return new TextractMarkdownNativeExtractorAdapter(extractionService, objectMapper,
                properties.getTextractVersion(), repository, normalizer, renderer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DocumentConvertService.class)
    MarkdownConversionPort markdownConversionPort(DocumentConvertService service) {
        return new DocumentConvertMarkdownAdapter(service);
    }

    @Bean
    @ConditionalOnMissingBean
    MarkdownTextBlockParser markdownTextBlockParser() {
        return new MarkdownTextBlockParser();
    }

    @Bean
    @ConditionalOnMissingBean
    NormalizedMarkdownRenderer normalizedMarkdownRenderer() {
        return new NormalizedMarkdownRenderer();
    }

    @Bean
    @ConditionalOnMissingBean
    ParsedFileNormalizedDocumentMapper parsedFileNormalizedDocumentMapper() {
        return new ParsedFileNormalizedDocumentMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    MarkdownNormalizationPort markdownNormalizationPort(MarkdownTextBlockParser parser,
            NormalizedMarkdownRenderer renderer,
            ObjectMapper objectMapper) {
        return new DefaultMarkdownNormalizationPort(parser, renderer, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    MarkdownPagePreviewPort markdownPagePreviewPort() {
        return new PdfBoxMarkdownPagePreviewAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    MarkdownPipelinePort markdownPipelinePort(ObjectProvider<RagIndexJobService> ragIndexJobs,
            ObjectProvider<SkillRagExtractionJobService> skillJobService,
            ObjectProvider<ChunkingOrchestrator> chunking,
            ObjectProvider<RagChunkStageStore> chunkStageStore,
            ObjectProvider<EmbeddingPort> embeddingPort,
            ObjectProvider<AiProviderRegistry> aiProviderRegistry,
            ObjectProvider<ChunkSetStore> chunkSetStore,
            MarkdownRepository repository,
            ObjectMapper objectMapper) {
        return new MarkdownDownstreamPipelineAdapter(ragIndexJobs, skillJobService, chunking, chunkStageStore,
                embeddingPort, aiProviderRegistry, chunkSetStore, repository, objectMapper);
    }

    @Bean(name = "markdownTaskExecutor")
    @ConditionalOnMissingBean(name = "markdownTaskExecutor")
    TaskExecutor markdownTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("markdown-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    MarkdownTaskExecutor markdownTaskScheduler(
            @Qualifier("markdownTaskExecutor") TaskExecutor markdownTaskExecutor) {
        return new AfterCommitMarkdownTaskExecutor(markdownTaskExecutor);
    }

    @Bean
    @ConditionalOnMissingBean(name = "markdownRagObjectMetadataContributor")
    RagObjectMetadataContributor markdownRagObjectMetadataContributor(MarkdownRepository repository) {
        return new MarkdownRagObjectMetadataContributor(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    MarkdownTransactionOperations markdownTransactionOperations(
            ObjectProvider<PlatformTransactionManager> transactionManager) {
        PlatformTransactionManager manager = transactionManager.getIfAvailable();
        if (manager == null) {
            return MarkdownTransactionOperations.direct();
        }
        TransactionTemplate template = new TransactionTemplate(manager);
        return new MarkdownTransactionOperations() {
            @Override
            public <T> T required(java.util.function.Supplier<T> action) {
                return template.execute(status -> action.get());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({MarkdownRepository.class, MarkdownSourcePort.class, MarkdownNativeExtractorPort.class,
            MarkdownConversionPort.class})
    MarkdownDocumentService markdownDocumentService(MarkdownRepository repository, MarkdownSourcePort sourcePort,
            MarkdownNativeExtractorPort nativeExtractor, MarkdownConversionPort conversionPort,
            MarkdownNormalizationPort normalizationPort,
            MarkdownPagePreviewPort pagePreviewPort,
            MarkdownPipelinePort pipelinePort, MarkdownTaskExecutor taskExecutor,
            MarkdownTransactionOperations transactions,
            ObjectMapper objectMapper, MarkdownProperties properties) {
        return new MarkdownDocumentService(repository, sourcePort, nativeExtractor, conversionPort,
                normalizationPort, pipelinePort, taskExecutor, transactions,
                objectMapper, Clock.systemUTC(), properties.getPandocVersion(),
                Set.copyOf(properties.getPandocFormats()), properties.isFallbackToNativeOnPandocFailure(),
                properties.getResultCacheDir(), pagePreviewPort, properties.getWeb().getBasePath());
    }

    @Bean
    @ConditionalOnMissingBean(name = "markdownDocumentConvertListener")
    @ConditionalOnBean(MarkdownDocumentService.class)
    DocumentConvertJobListener markdownDocumentConvertListener(MarkdownDocumentService service) {
        return new MarkdownDocumentConvertListener(service);
    }

    @Bean
    @ConditionalOnMissingBean(name = "markdownDocumentConvertDirectResultStore")
    @ConditionalOnBean(MarkdownDocumentService.class)
    DocumentConvertDirectResultStore markdownDocumentConvertDirectResultStore(
            MarkdownDocumentService service,
            MarkdownProperties properties) {
        return new MarkdownDocumentConvertDirectResultStore(service, properties.getMaxSourceBytes());
    }
}
