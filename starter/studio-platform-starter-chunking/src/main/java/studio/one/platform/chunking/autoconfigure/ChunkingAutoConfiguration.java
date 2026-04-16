package studio.one.platform.chunking.autoconfigure;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import studio.one.platform.chunking.core.Chunker;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.service.DefaultChunkingOrchestrator;
import studio.one.platform.chunking.service.FixedSizeChunker;
import studio.one.platform.chunking.service.RecursiveChunker;

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
    @ConditionalOnMissingBean(ChunkingOrchestrator.class)
    public ChunkingOrchestrator chunkingOrchestrator(
            ChunkingProperties properties,
            List<Chunker> chunkers) {
        properties.validate();
        return new DefaultChunkingOrchestrator(properties, chunkers);
    }
}
