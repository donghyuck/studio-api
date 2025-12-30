/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file PlatformSecurityAutoConfigurationtion.java
 *      @date 2025
 *
 */
package studio.one.platform.security.autoconfigure;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.authentication.lock.service.AccountLockService;
import studio.one.base.security.handler.AuthenticationErrorHandler;
import studio.one.base.security.userdetails.ApplicationUserDetailsService;
import studio.one.base.user.service.ApplicationUserService;

import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.security.authz.AllowAllEndpointAuthorization;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

/**
 * Auto-configuration for security-related infrastructure used by the platform.
 *
 * <p>
 * This configuration class conditionally registers a set of commonly-required
 * security beans
 * when Spring Security and related types are present on the classpath and the
 * security feature
 * is enabled via configuration.
 *
 * <p>
 * Key behaviors:
 * <ul>
 * <li>Only activates when the property {@code <security-prefix>.enabled} is set
 * to {@code true}
 * (see
 * {@link studio.one.platform.security.autoconfigure.PropertyKeys.Security}) and
 * when
 * {@link org.springframework.security.config.annotation.web.builders.HttpSecurity}
 * and
 * {@link org.springframework.security.crypto.password.PasswordEncoder} are
 * available on the
 * classpath.</li>
 * <li>Beans are registered only if no user-provided beans of the same type (or
 * name) exist,
 * allowing easy customization by defining application beans to override
 * defaults.</li>
 * </ul>
 *
 * <p>
 * Provided beans (defaults):
 * <ul>
 * <li>{@code PasswordEncoder} (bean name:
 * {@code ServiceNames.PASSWORD_ENCODER}):
 * - A
 * {@link org.springframework.security.crypto.password.DelegatingPasswordEncoder}
 * backed
 * by multiple encoders: BCrypt (default), PBKDF2, and SHA-256 (legacy /
 * deprecated).
 * - Defaults: BCrypt strength = 10; PBKDF2 iterations = 185000; PBKDF2 hash
 * width = 256;
 * PBKDF2 secret = empty string.
 * - Selected encoding id is determined by
 * {@code properties.security.password.encoder.algorithm}.
 * If {@code CUSTOM} is chosen, the auto-config keeps BCrypt as the safe
 * default; to use a
 * custom algorithm, the application must register its own PasswordEncoder bean.
 * - A runtime warning is logged if SHA-256 is chosen (deprecated / not
 * recommended).</li>
 *
 * <li>{@code CorsConfigurationSource} (primary, bean name:
 * {@code ServiceNames.CORS_CONFIGURATION_SOURCE}):
 * - Created when CORS is enabled under {@code properties.security.cors.enabled}
 * (default:
 * enabled if not explicitly disabled).
 * - Configured from {@code SecurityProperties.getCors()} (allowed origins,
 * methods,
 * headers, exposed headers, allowCredentials, maxAge).
 * - Registers the CORS configuration for the pattern {@code /**} using
 * {@link org.springframework.web.cors.UrlBasedCorsConfigurationSource}.</li>
 *
 * <li>{@code AuthenticationErrorHandler}:
 * - A JSON-capable error handler for authentication failures; created only if
 * the type
 * {@code AuthenticationErrorHandler} is present and no other handler bean
 * exists.</li>
 *
 * <li>{@code AuthenticationManager} (bean name:
 * {@code ServiceNames.AUTHENTICATION_MANAGER}):
 * - Built from the shared
 * {@link org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder}
 * exposed by the provided
 * {@link org.springframework.security.config.annotation.web.builders.HttpSecurity}.
 * - Configured with the auto-configured {@code UserDetailsService} and the
 * resolved
 * {@code PasswordEncoder}.</li>
 *
 * <li>{@code UserDetailsService} (bean name:
 * {@code ServiceNames.USER_DETAILS_SERVICE}):
 * - Default implementation wraps {@code ApplicationUserService} via
 * {@code ApplicationUserDetailsService} when {@code ApplicationUserService} is
 * present.
 * - An {@code AccountLockService} may be injected if available to support
 * account locking.</li>
 * </ul>
 *
 * <p>
 * Extension points and customization:
 * <ul>
 * <li>To replace any provided bean, declare a bean of the same type (or the
 * same name for named
 * beans). The auto-configuration backs off when a matching bean already
 * exists.</li>
 * <li>To change password encoding algorithm or parameters, set properties under
 * {@code properties.security.password.encoder} (algorithm, bcryptStrength,
 * iterations,
 * hashWidth, secret). For a custom encoder implementation, set the algorithm to
 * CUSTOM and
 * register your
 * {@link org.springframework.security.crypto.password.PasswordEncoder}
 * bean.</li>
 * <li>To disable the entire security auto-configuration, set
 * {@code <security-prefix>.enabled=false}.</li>
 * <li>To tune CORS, configure {@code properties.security.cors.*}. CORS
 * auto-configuration is
 * enabled by default (matchIfMissing = true) unless explicitly disabled.</li>
 * </ul>
 *
 * <p>
 * Logging and i18n:
 * <ul>
 * <li>Initialization and state transitions are logged with localized messages
 * resolved via an
 * {@code I18n} provider when available.</li>
 * <li>Debug logging emits detailed configuration values for troubleshooting;
 * deprecated choices
 * (e.g. SHA-256) produce a warning message advising against use.</li>
 * </ul>
 *
 * <p>
 * Notes:
 * <ul>
 * <li>The class relies on a number of platform constants (e.g.
 * {@code PropertyKeys}, {@code ServiceNames})
 * and helper utilities for logging and i18n used across the project's
 * auto-configuration modules.</li>
 * <li>This auto-configuration is intended to provide sensible, secure defaults
 * while allowing
 * applications to opt-in or override behavior where stricter or different
 * security semantics
 * are required.</li>
 * </ul>
 *
 * @see org.springframework.boot.autoconfigure.AutoConfiguration
 * @see org.springframework.security.crypto.password.PasswordEncoder
 * @see org.springframework.web.cors.CorsConfigurationSource
 * 
 * 
 * @author donghyuck, son
 * @since 2025-11-18
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-18  donghyuck, son: 최초 생성.
 *          </pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
@AutoConfigureAfter(name = "studio.one.platform.user.autoconfigure.UserServicesAutoConfiguration")
@ConditionalOnClass({ HttpSecurity.class, PasswordEncoder.class })
@ConditionalOnProperty(prefix = PropertyKeys.Security.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("java:S1118")
public class SecurityAutoConfiguration {

        protected static final String FEATURE_NAME = "Security";

        /**
         * PasswordEncoder 빈을 정의합니다.
         * <ul>
         * <li>기본적으로 BCryptPasswordEncoder를 사용합니다.</li>
         * <li>PBKDF2, SHA-256 등 다른 알고리즘도 지원합니다.</li>
         * <li>커스텀 구현체를 사용하려면 properties.security.password.encoder.algorithm=CUSTOM 설정 후
         * 별도 빈 등록 필요</li>
         * </ul>
         *
         * @param properties   SecurityProperties
         * @param i18nProvider I18n 프로바이더
         * @return PasswordEncoder
         */
        @Bean(name = ServiceNames.PASSWORD_ENCODER)
        @ConditionalOnMissingBean(PasswordEncoder.class)
        PasswordEncoder passwordEncoder(SecurityProperties properties, ObjectProvider<I18n> i18nProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                PasswordEncoderProperties props = properties.getPasswordEncoder();
                Map<String, PasswordEncoder> encoders = new HashMap<>();
                // 1) Bcrypt
                int strength = props.getBcryptStrength() != null ? props.getBcryptStrength() : 10;
                encoders.put(PasswordEncoderProperties.Algorithm.BCRYPT.name().toLowerCase(),
                                new BCryptPasswordEncoder(strength));

                // 2) PBKDF2
                Integer iterations = props.getIterations() != null ? props.getIterations() : 185000;
                Integer hashWidth = props.getHashWidth() != null ? props.getHashWidth() : 256;
                String secret = props.getSecret() != null ? props.getSecret() : "";
                Pbkdf2PasswordEncoder pbkdf2 = new Pbkdf2PasswordEncoder(secret, iterations, hashWidth);
                encoders.put(PasswordEncoderProperties.Algorithm.PBKDF2.name().toLowerCase(), pbkdf2);

                // 3) SHA-256 (레거시 호환용만—비권장)
                encoders.put(PasswordEncoderProperties.Algorithm.SHA256.name().toLowerCase(),
                                new MessageDigestPasswordEncoder("SHA-256"));

                String idForEncode = PasswordEncoderProperties.Algorithm.BCRYPT.name().toLowerCase();

                PasswordEncoderProperties.Algorithm alg = props.getAlgorithm();
                if (alg == PasswordEncoderProperties.Algorithm.PBKDF2) {
                        idForEncode = PasswordEncoderProperties.Algorithm.PBKDF2.name().toLowerCase();
                } else if (alg == PasswordEncoderProperties.Algorithm.SHA256) {
                        log.warn(LogUtils.red(i18n.get("warn.security.password.encoder.sha256.deprecated")));
                        idForEncode = PasswordEncoderProperties.Algorithm.SHA256.name().toLowerCase();
                } else if (alg == PasswordEncoderProperties.Algorithm.CUSTOM) {
                        // 커스텀 미구현 시 안전 기본값 유지
                        idForEncode = PasswordEncoderProperties.Algorithm.BCRYPT.name().toLowerCase();
                }
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(PasswordEncoder.class, true),
                                LogUtils.red(State.CREATED.toString())));

                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.INFO + I18nKeys.AutoConfig.Feature.Service.INIT,
                                FEATURE_NAME,
                                LogUtils.blue(PasswordEncoder.class, true),
                                "Algorithm", LogUtils.green(props.getAlgorithm().name())));
                return new DelegatingPasswordEncoder(idForEncode, encoders);
        }

        @Bean(name = ServiceNames.DOMAIN_ENDPOINT_AUTHZ)
        @ConditionalOnMissingBean(name = ServiceNames.DOMAIN_ENDPOINT_AUTHZ)
        public AllowAllEndpointAuthorization endpointAuthorizationFallback(ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.warn(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(AllowAllEndpointAuthorization.class, true),
                                LogUtils.red("FALLBACK_ALLOW_ALL")));
                return new AllowAllEndpointAuthorization();
        }

        /*
         * CORS 설정을 위한 CorsConfigurationSource 빈을 정의합니다.
         */
        @Primary
        @Bean(name = ServiceNames.CORS_CONFIGURATION_SOURCE)
        @ConditionalOnMissingBean(name = ServiceNames.CORS_CONFIGURATION_SOURCE)
        @ConditionalOnProperty(prefix = PropertyKeys.Security.Cors.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
        public CorsConfigurationSource corsConfigurationSource(
                        SecurityProperties properties,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                CorsConfiguration config = new CorsConfiguration();

                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(CorsConfiguration.class, true),
                                LogUtils.red(State.CREATED.toString())));

                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(CorsConfiguration.class, true),
                                LogUtils.red(State.INITIALIZING.toString())));

                var cors = properties.getCors();
                if (log.isDebugEnabled()) {
                        log.debug(i18n.get("debug.security.cors.configuration.details",
                                        join(cors.getAllowedOrigins()),
                                        join(cors.getAllowedOriginPatterns()),
                                        join(cors.getAllowedMethods()),
                                        join(cors.getAllowedHeaders()),
                                        join(cors.getExposedHeaders()),
                                        String.valueOf(Boolean.TRUE.equals(cors.getAllowCredentials())),
                                        cors.getMaxAge() != null ? cors.getMaxAge() : "-"));
                }
                config.setAllowedOrigins(cors.getAllowedOrigins());
                config.setAllowedMethods(cors.getAllowedMethods());
                config.setAllowedHeaders(cors.getAllowedHeaders());
                config.setExposedHeaders(cors.getExposedHeaders());
                config.setAllowCredentials(cors.getAllowCredentials());
                config.setMaxAge(cors.getMaxAge());
                log.info(LogUtils.green(i18n.get("info.security.cors.configuration.initialized")));

                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(CorsConfiguration.class, true),
                                LogUtils.red(State.INITIALIZED.toString())));

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                java.util.Map<String, org.springframework.web.cors.CorsConfiguration> map = source
                                .getCorsConfigurations();
                if (log.isDebugEnabled()) {
                        if (map != null && !map.isEmpty()) {
                                map.forEach((path,
                                                cfg) -> log.debug(i18n.get("debug.security.cors.configuration.mapped",
                                                                path,
                                                                join(cfg.getAllowedOrigins()),
                                                                join(cfg.getAllowedOriginPatterns()),
                                                                join(cfg.getAllowedMethods()),
                                                                join(cfg.getAllowedHeaders()),
                                                                join(cfg.getExposedHeaders()),
                                                                String.valueOf(Boolean.TRUE
                                                                                .equals(cfg.getAllowCredentials())),
                                                                cfg.getMaxAge() != null ? cfg.getMaxAge() : "-")));
                        } else {
                                log.warn(i18n.get("warn.security.cors.configuration.source.unknown",
                                                source.getClass().getName()));
                        }
                }
                return source;
        }

        /**
         * AuthenticationErrorHandler 빈을 정의합니다. 이클래스는 인증 오류 처리를 담당합니다.
         * 
         * @param objectMapper
         * @param i18nProvider
         * @return
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnClass({ AuthenticationErrorHandler.class })
        public AuthenticationErrorHandler authenticationErrorHandler(
                        ObjectMapper objectMapper,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(AuthenticationErrorHandler.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new AuthenticationErrorHandler(objectMapper, i18n);
        }

        @Bean
        @ConditionalOnMissingBean(DaoAuthenticationProvider.class)
        public DaoAuthenticationProvider daoAuthenticationProvider(
                        @Qualifier(ServiceNames.USER_DETAILS_SERVICE) UserDetailsService uds,
                        @Qualifier(ServiceNames.PASSWORD_ENCODER) PasswordEncoder pe) {

                DaoAuthenticationProvider p = new DaoAuthenticationProvider();
                p.setUserDetailsService(uds);
                p.setPasswordEncoder(pe);
                return p;
        }

        @Bean(name = ServiceNames.AUTHENTICATION_MANAGER)
        @ConditionalOnMissingBean(AuthenticationManager.class)
        public AuthenticationManager authenticationManager(
                        DaoAuthenticationProvider daoProvider,
                        AuthenticationEventPublisher eventPublisher,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(AuthenticationManager.class, true),
                                LogUtils.red(State.CREATED.toString())));
                ProviderManager pm = new ProviderManager(daoProvider);
                pm.setAuthenticationEventPublisher(eventPublisher);
                return pm;
        }

        /**
         * UserDetailsService 빈을 정의합니다.
         * 
         * @param userService
         * @param accountLockService
         * @param i18nProvider
         * @return
         */
        @Primary
        @Bean(ServiceNames.USER_DETAILS_SERVICE)
        @ConditionalOnMissingBean(ApplicationUserDetailsService.class)
        @ConditionalOnClass({ ApplicationUserService.class })
        public UserDetailsService applicationUserDetailsService(
                        ApplicationUserService userService,
                        ObjectProvider<AccountLockService> accountLockService,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(ApplicationUserDetailsService.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new ApplicationUserDetailsService(userService, accountLockService);
        }

        private static String join(java.util.List<String> values) {
                return (values == null || values.isEmpty()) ? "-" : String.join(", ", values);
        }

}
