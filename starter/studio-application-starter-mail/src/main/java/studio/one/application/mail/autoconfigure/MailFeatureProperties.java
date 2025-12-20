package studio.one.application.mail.autoconfigure;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.application.mail.config.ImapProperties;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.autoconfigure.SimpleWebProperties;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".mail")
@Getter
@Setter
@Validated
@EqualsAndHashCode(callSuper = true)
public class MailFeatureProperties extends FeatureToggle {

    @Valid
    @NotNull
    private ImapProperties imap = new ImapProperties();

    private Web web = new Web();

    public studio.one.platform.autoconfigure.PersistenceProperties.Type resolvePersistence(
            studio.one.platform.autoconfigure.PersistenceProperties.Type globalDefault) {
        return super.resolvePersistence(globalDefault);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Web extends SimpleWebProperties {

        public enum NotifyTransport {
            sse,
            stomp
        }

        private NotifyTransport notify = NotifyTransport.sse;
        private Boolean sse;
        private String stompDestination = "/mail-sync";

        public NotifyTransport resolveNotify() {
            if (sse != null) {
                return sse ? NotifyTransport.sse : NotifyTransport.stomp;
            }
            return notify == null ? NotifyTransport.sse : notify;
        }

    }

}
