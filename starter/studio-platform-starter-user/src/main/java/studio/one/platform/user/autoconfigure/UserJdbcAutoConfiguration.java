package studio.one.platform.user.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.persistence.ApplicationUserRepository;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.user.autoconfigure.condition.ConditionalOnUserPersistence;

@AutoConfiguration
@EnableConfigurationProperties({ PersistenceProperties.class, UserFeatureProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "enabled", havingValue = "true")
@Slf4j
public class UserJdbcAutoConfiguration {

    /**
     * JDBC 스캔
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(ApplicationUserRepository.class)
    @ConditionalOnUserPersistence(PersistenceProperties.Type.jdbc)
    @ComponentScan(basePackages = "${" + PropertyKeys.Features.User.PREFIX + ".jdbc-repository-packages:" + UserFeatureProperties.JDBC_REPOSITORY_PACKAGE + "}")
    static class JdbcWiring {

    }
}
