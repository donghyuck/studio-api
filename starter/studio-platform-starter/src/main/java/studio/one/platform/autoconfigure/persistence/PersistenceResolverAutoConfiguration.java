package studio.one.platform.autoconfigure.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class PersistenceResolverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PersistenceTypeResolver persistenceTypeResolver(Environment environment) {
        return new EnvironmentPersistenceTypeResolver(environment);
    }
}
