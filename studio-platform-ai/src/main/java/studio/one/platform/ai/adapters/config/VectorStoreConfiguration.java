package studio.one.platform.ai.adapters.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.one.platform.ai.adapters.vector.PgVectorStoreAdapter;
import studio.one.platform.ai.core.vector.VectorStorePort;

@Configuration
public class VectorStoreConfiguration {

    @Bean
    @ConditionalOnMissingBean(VectorStorePort.class)
    @ConditionalOnBean(JdbcTemplate.class)
    public VectorStorePort vectorStorePort(JdbcTemplate jdbcTemplate) {
        return new PgVectorStoreAdapter(jdbcTemplate);
    }
}
