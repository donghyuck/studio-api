package studio.echo.platform.security.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.jwt.JwtTokenProvider;
import studio.echo.base.security.web.controller.JwtAuthController;
import studio.echo.platform.autoconfigure.i18n.I18nKeys;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.I18nUtils;
import studio.echo.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Security.Jwt.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(SecurityAutoConfiguration.class)
@RequiredArgsConstructor
@Slf4j
public class JwtEndpointsAutoConfiguration {

    private final SecurityProperties securityProperties;

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Security.Jwt.Endpoints.PREFIX, name = "login-enabled", havingValue = "true")
    public JwtAuthController jwtEndpoint(JwtTokenProvider jwtTokenProvider, AuthenticationManager authenticationManager, ObjectProvider<I18n> i18nProvider) {
         I18n i18n = I18nUtils.resolve(i18nProvider);
        JwtProperties props = securityProperties.getJwt();
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, "Security",
                LogUtils.blue("Jwt"),
                LogUtils.blue(JwtAuthController.class, true),
                props.getEndpoints().getBasePath(), getModeString(props)) );
        return new JwtAuthController(authenticationManager, jwtTokenProvider);
    }

    private String getModeString(JwtProperties props)
    {
        boolean access =  props.getEndpoints().isLoginEnabled();
        boolean refresh =  props.getEndpoints().isRefreshEnabled();
        if( access && refresh ){
            return "ACCESS & REFRESH";
        } else if (access){
            return "ACCESS ONLY";
        } else if ( refresh ){
            return "REFRESH ONLY";
        } else {
            return "NONE";
        }
    }

}
