package studio.one.platform.security.autoconfigure;

import java.time.Clock;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.authentication.lock.persistence.jdbc.AccountLockJdbcRepository;
import studio.one.base.security.authentication.lock.persistence.jpa.AccountLockJpaRepository;
import studio.one.base.security.authentication.lock.service.AccountLockService;
import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.base.security.jwt.refresh.HashedRefreshTokenStore;
import studio.one.base.security.jwt.refresh.RefreshTokenStore;
import studio.one.base.security.jwt.refresh.persistence.RefreshTokenRepository;
import studio.one.base.security.jwt.refresh.persistence.jdbc.RefreshTokenJdbcRepository;
import studio.one.base.security.jwt.refresh.persistence.jpa.RefreshTokenJpaRepository;
import studio.one.base.security.web.controller.JwtAuthController;
import studio.one.base.security.web.controller.JwtRefreshController;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.security.autoconfigure.condition.ConditionalOnJwtPersistence;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties({ SecurityProperties.class, PersistenceProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Security.Jwt.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(SecurityAutoConfiguration.class)
@RequiredArgsConstructor
@Slf4j
public class JwtSecurtyAutoConfiguration {

    private static final String FEATURE_NAME = "Security - Jwt";
    private static final String DEFAULT_JPA_ENTITY_PACKAGE = RefreshTokenStore.class.getPackageName();
    private static final String DEFAULT_JPA_REPOSITORY_PACKAGE = "studio.one.base.security.jwt.refresh.persistence.jpa";

    private final SecurityProperties securityProperties;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = PropertyKeys.Security.Jwt.PREFIX
            + ".jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Clock jwtClock(ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(Clock.class, true), LogUtils.red(State.CREATED.toString())));
        return Clock.systemUTC();
    }

    @Bean(ServiceNames.JWT_TOKEN_PROVIDER)
    @ConditionalOnMissingBean
    @ConditionalOnClass({ JwtTokenProvider.class })
    @ConditionalOnProperty(prefix = PropertyKeys.Security.Jwt.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    public JwtTokenProvider jwtTokenProvider(
            Clock jwtClock,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        var jwtProps = securityProperties.getJwt();
        JwtTokenProvider provider = new JwtTokenProvider(
                jwtProps,
                jwtClock,
                i18n);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(JwtTokenProvider.class, true), LogUtils.red(State.CREATED.toString())));
        return provider;
    }

    @Bean
    @ConditionalOnClass({ RefreshTokenStore.class })
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = PropertyKeys.Security.Jwt.PREFIX
            + ".jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RefreshTokenStore refreshTokenStore(
            RefreshTokenRepository refreshTokenRepository,
            Clock jwtClock,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        JwtProperties props = securityProperties.getJwt();
        boolean isJdbc = refreshTokenRepository instanceof RefreshTokenJdbcRepository;
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(RefreshTokenStore.class, true), LogUtils.red(State.CREATED.toString())));

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.INFO + I18nKeys.AutoConfig.Feature.Service.INIT,
                FEATURE_NAME,
                LogUtils.blue(AccountLockService.class, true),
                "RefreshTokenRepository",
                LogUtils.green(isJdbc ? RefreshTokenJdbcRepository.class : RefreshTokenJpaRepository.class, true)));
        return new HashedRefreshTokenStore(refreshTokenRepository, jwtClock, props);
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Security.Jwt.Endpoints.PREFIX, name = "login-enabled", havingValue = "true")
    public JwtAuthController jwtLoginEndpoint(
            JwtTokenProvider jwtTokenProvider,
            AuthenticationManager authenticationManager,
            ObjectProvider<AccountLockService> accountLockService,
            ObjectProvider<RefreshTokenStore> storeProvider,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        JwtProperties props = securityProperties.getJwt();
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue("Jwt"),
                LogUtils.blue(JwtAuthController.class, true),
                props.getEndpoints().getBasePath(), getModeString(props)));
        return new JwtAuthController(authenticationManager, jwtTokenProvider, storeProvider,
                I18nUtils.resolve(i18nProvider));
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Security.Jwt.Endpoints.PREFIX, name = "login-enabled", havingValue = "true")
    public JwtRefreshController jwtRefreshEndpoint(
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenStore refreshTokenStore,
            UserDetailsService userDetailsService,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        JwtProperties props = securityProperties.getJwt();
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue("Jwt"),
                LogUtils.blue(JwtRefreshController.class, true),
                props.getEndpoints().getBasePath(), getModeString(props)));
        return new JwtRefreshController(jwtTokenProvider, refreshTokenStore, userDetailsService,
                I18nUtils.resolve(i18nProvider));
    }

    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
    @ConditionalOnJwtPersistence(PersistenceProperties.Type.jpa)
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor jwtEntityScanRegistrar(Environment env, I18n i18n) {
            String entityKey = PropertyKeys.Security.Jwt.PREFIX + ".entity-packages";
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EntityScan.PREPARING, FEATURE_NAME, entityKey,
                    DEFAULT_JPA_ENTITY_PACKAGE));
            return EntityScanRegistrarSupport.entityScanRegistrar(entityKey + ".entity-packages",
                    DEFAULT_JPA_ENTITY_PACKAGE);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnJwtPersistence(PersistenceProperties.Type.jpa)
    @EnableJpaRepositories(basePackages = DEFAULT_JPA_REPOSITORY_PACKAGE)
    static class JpaWiring {

    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnJwtPersistence(PersistenceProperties.Type.jdbc)
    static class JdbcWiring {

        @Bean
        @ConditionalOnMissingBean(RefreshTokenRepository.class)
        RefreshTokenRepository refreshTokenJdbcRepository(NamedParameterJdbcTemplate template) {
            return new RefreshTokenJdbcRepository(template);
        }
    }

    private String getModeString(JwtProperties props) {
        boolean access = props.getEndpoints().isLoginEnabled();
        boolean refresh = props.getEndpoints().isRefreshEnabled();
        if (access && refresh) {
            return "ACCESS & REFRESH";
        } else if (access) {
            return "ACCESS ONLY";
        } else if (refresh) {
            return "REFRESH ONLY";
        } else {
            return "NONE";
        }
    }

}
