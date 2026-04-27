package studio.one.platform.chunking.autoconfigure;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import studio.one.platform.chunking.core.Chunker;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.service.DefaultChunkingOrchestrator;
import studio.one.platform.chunking.service.FixedSizeChunker;
import studio.one.platform.chunking.service.HeadingChunkContextExpander;
import studio.one.platform.chunking.service.ParentChildChunkContextExpander;
import studio.one.platform.chunking.service.RecursiveChunker;
import studio.one.platform.chunking.service.StructureBasedChunker;
import studio.one.platform.chunking.service.TableChunkContextExpander;
import studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter;
import studio.one.platform.chunking.service.WindowChunkContextExpander;

@AutoConfiguration
@EnableConfigurationProperties(ChunkingProperties.class)
@ConditionalOnProperty(prefix = "studio.chunking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChunkingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FixedSizeChunker fixedSizeChunker(ChunkingProperties properties) {
        return new FixedSizeChunker(properties.getMaxSize(), properties.getOverlap());
    }

    @Bean
    @ConditionalOnMissingBean
    public RecursiveChunker recursiveChunker(ChunkingProperties properties) {
        return new RecursiveChunker(properties.getMaxSize(), properties.getOverlap());
    }

    @Bean
    @ConditionalOnMissingBean
    public StructureBasedChunker structureBasedChunker(
            ChunkingProperties properties,
            RecursiveChunker recursiveChunker) {
        return new StructureBasedChunker(properties.getMaxSize(), properties.getOverlap(), recursiveChunker);
    }

    @Bean
    @ConditionalOnMissingBean
    public WindowChunkContextExpander windowChunkContextExpander() {
        return new WindowChunkContextExpander();
    }

    @Bean
    @ConditionalOnMissingBean
    public ParentChildChunkContextExpander parentChildChunkContextExpander() {
        return new ParentChildChunkContextExpander();
    }

    @Bean
    @ConditionalOnMissingBean
    public HeadingChunkContextExpander headingChunkContextExpander() {
        return new HeadingChunkContextExpander();
    }

    @Bean
    @ConditionalOnMissingBean
    public TableChunkContextExpander tableChunkContextExpander() {
        return new TableChunkContextExpander();
    }

    @Bean
    @ConditionalOnMissingBean(ChunkingOrchestrator.class)
    public ChunkingOrchestrator chunkingOrchestrator(
            ChunkingProperties properties,
            List<Chunker> chunkers) {
        properties.validate();
        return new DefaultChunkingOrchestrator(properties, chunkers);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "studio.one.platform.textract.model.ParsedFile")
    static class TextractAdapterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TextractNormalizedDocumentAdapter textractNormalizedDocumentAdapter() {
            return new TextractNormalizedDocumentAdapter();
        }
    }
}
