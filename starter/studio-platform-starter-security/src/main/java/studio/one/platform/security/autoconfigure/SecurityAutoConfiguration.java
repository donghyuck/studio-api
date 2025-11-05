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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
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
import studio.one.base.security.authentication.AccountLockService;
import studio.one.base.security.handler.AuthenticationErrorHandler;
import studio.one.base.security.userdetails.ApplicationUserDetailsService;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.platform.autoconfigure.i18n.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
@AutoConfigureAfter(name = "studio.one.platform.user.autoconfigure.UserServicesAutoConfiguration")
@ConditionalOnClass({ HttpSecurity.class, PasswordEncoder.class })
@ConditionalOnProperty(prefix = PropertyKeys.Security.PREFIX, name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("java:S1118")
public class SecurityAutoConfiguration {

    private static final String FEATURE_NAME = "Security";

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
        log.info(i18n.get("info.security.password.encoder.initialized", LogUtils.green(props.getAlgorithm().name())));

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(PasswordEncoder.class, true),
                LogUtils.red(State.CREATED.toString())));
        return new DelegatingPasswordEncoder(idForEncode, encoders);
    }

    @Primary
    @Bean(name = ServiceNames.CORS_CONFIGURATION_SOURCE)
    @ConditionalOnMissingBean(name = ServiceNames.CORS_CONFIGURATION_SOURCE)
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties,
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
        java.util.Map<String, org.springframework.web.cors.CorsConfiguration> map = source.getCorsConfigurations();
        if (log.isDebugEnabled()) {
            if (map != null && !map.isEmpty()) {
                map.forEach((path, cfg) -> log.debug(i18n.get("debug.security.cors.configuration.mapped",
                        path,
                        join(cfg.getAllowedOrigins()),
                        join(cfg.getAllowedOriginPatterns()),
                        join(cfg.getAllowedMethods()),
                        join(cfg.getAllowedHeaders()),
                        join(cfg.getExposedHeaders()),
                        String.valueOf(Boolean.TRUE.equals(cfg.getAllowCredentials())),
                        cfg.getMaxAge() != null ? cfg.getMaxAge() : "-")));
            } else {
                log.warn(i18n.get("warn.security.cors.configuration.source.unknown", source.getClass().getName()));
            }
        }
        return source;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass({ AuthenticationErrorHandler.class })
    public AuthenticationErrorHandler authenticationErrorHandler(ObjectMapper objectMapper,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(AuthenticationErrorHandler.class, true), LogUtils.red(State.CREATED.toString())));

        return new AuthenticationErrorHandler(objectMapper, i18n);
    }

    @Bean(ServiceNames.AUTHENTICATION_MANAGER)
    @ConditionalOnMissingBean
    public AuthenticationManager authenticationManager(
            HttpSecurity http,
            @Qualifier(ServiceNames.USER_DETAILS_SERVICE) UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            ObjectProvider<I18n> i18nProvider) throws Exception {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(AuthenticationManager.class, true), LogUtils.red(State.CREATED.toString())));
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder)
                .and()
                .build();
    }

    @Bean(ServiceNames.USER_DETAILS_SERVICE)
    @ConditionalOnMissingBean(ApplicationUserDetailsService.class)
    @ConditionalOnClass({ ApplicationUserService.class })
    public UserDetailsService applicationUserDetailsService(
            ApplicationUserService<User, Role> userServce,
            ObjectProvider<AccountLockService> accountLockService,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(ApplicationUserDetailsService.class, true), LogUtils.red(State.CREATED.toString())));
        return new ApplicationUserDetailsService(userServce, accountLockService);
    }

    private static String join(java.util.List<String> values) {
        return (values == null || values.isEmpty()) ? "-" : String.join(", ", values);
    }

}