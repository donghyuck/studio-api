package studio.one.platform.security.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

import jakarta.annotation.PostConstruct;

@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnProperty(prefix = "studio.security.jwt", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class JwtSecretPresenceGuard {

    private final SecurityProperties securityProperties;

    @PostConstruct
    void validate() {
        JwtProperties jwt = securityProperties.getJwt();
        if (!StringUtils.hasText(jwt.getSecret())) {
            throw new IllegalStateException("studio.security.jwt.secret must be configured when JWT is enabled");
        }
    }
}
