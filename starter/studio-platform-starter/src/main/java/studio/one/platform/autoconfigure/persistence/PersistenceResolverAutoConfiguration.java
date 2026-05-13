package studio.one.platform.autoconfigure.persistence;

import org.springframework.context.annotation.Configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@Configuration
public class PersistenceResolverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PersistenceTypeResolver persistenceTypeResolver(Environment environment) {
        return new EnvironmentPersistenceTypeResolver(environment);
    }
}
