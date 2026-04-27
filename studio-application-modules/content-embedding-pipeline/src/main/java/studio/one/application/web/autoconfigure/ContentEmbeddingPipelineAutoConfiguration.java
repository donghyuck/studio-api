package studio.one.application.web.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.service.AttachmentRagIndexJobSourceExecutor;
import studio.one.application.web.service.AttachmentRagIndexService;
import studio.one.application.web.service.AttachmentStructuredRagIndexer;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.textract.service.FileContentExtractionService;

@AutoConfiguration
@AutoConfigureAfter(name = "studio.one.application.attachment.autoconfigure.AttachmentAutoConfiguration")
@ConditionalOnClass({AttachmentService.class, RagPipelineService.class})
@ConditionalOnBean(AttachmentService.class)
@Import(ContentEmbeddingPipelineStructuredRagAutoConfiguration.class)
public class ContentEmbeddingPipelineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AttachmentRagIndexService attachmentRagIndexService(
            AttachmentService attachmentService,
            ObjectProvider<FileContentExtractionService> textExtractionProvider,
            ObjectProvider<RagPipelineService> ragPipelineProvider,
            ObjectProvider<AttachmentStructuredRagIndexer> structuredRagIndexerProvider) {
        return new AttachmentRagIndexService(
                attachmentService,
                textExtractionProvider,
                ragPipelineProvider,
                structuredRagIndexerProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    AttachmentRagIndexJobSourceExecutor attachmentRagIndexJobSourceExecutor(
            AttachmentRagIndexService ragIndexService) {
        return new AttachmentRagIndexJobSourceExecutor(ragIndexService);
    }

}

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
        "studio.one.platform.chunking.core.ChunkingOrchestrator",
        "studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter",
        "studio.one.platform.textract.model.ParsedFile"
})
class ContentEmbeddingPipelineStructuredRagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AttachmentStructuredRagIndexer.class)
    studio.one.application.web.service.DefaultAttachmentStructuredRagIndexer defaultAttachmentStructuredRagIndexer(
            ObjectProvider<studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter>
                    normalizedDocumentAdapterProvider,
            ObjectProvider<studio.one.platform.chunking.core.ChunkingOrchestrator> chunkingOrchestratorProvider,
            ObjectProvider<studio.one.platform.ai.core.embedding.EmbeddingPort> embeddingPortProvider,
            ObjectProvider<studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver>
                    embeddingProfileResolverProvider,
            ObjectProvider<studio.one.platform.ai.core.vector.VectorStorePort> vectorStoreProvider) {
        return new studio.one.application.web.service.DefaultAttachmentStructuredRagIndexer(
                normalizedDocumentAdapterProvider,
                chunkingOrchestratorProvider,
                embeddingPortProvider,
                embeddingProfileResolverProvider,
                vectorStoreProvider);
    }
}
