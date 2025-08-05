package studio.api.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration("config:datasource")
@EnableConfigurationProperties
public class DataSourceConfig {

    @Bean(name = "dataSourceProperties.primary")
    @ConfigurationProperties("spring.datasource.primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "datasource.primary")
    @Primary
    public DataSource dataSource(@Qualifier("dataSourceProperties.primary")DataSourceProperties properties) {
        return properties
            .initializeDataSourceBuilder()
            .build();
    }

    @Bean(name = "transactionManager.primary")
    public PlatformTransactionManager transactionManager(@Qualifier("datasource.primary") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

}
