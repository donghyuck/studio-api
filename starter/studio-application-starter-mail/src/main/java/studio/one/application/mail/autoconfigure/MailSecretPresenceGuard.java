package studio.one.application.mail.autoconfigure;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.application.mail.config.ImapProperties;
import studio.one.platform.constant.PropertyKeys;

@AutoConfiguration
@EnableConfigurationProperties(MailFeatureProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".mail", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(MailImapPropertiesConfiguration.class)
@RequiredArgsConstructor
public class MailSecretPresenceGuard {

    private final ImapProperties imap;

    @PostConstruct
    void validate() {
        requireText(imap.getHost(), "studio.mail.imap.host must be configured");
        requireText(imap.getUsername(), "studio.mail.imap.username must be configured");
        requireText(imap.getPassword(), "studio.mail.imap.password must be configured");
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }
}
