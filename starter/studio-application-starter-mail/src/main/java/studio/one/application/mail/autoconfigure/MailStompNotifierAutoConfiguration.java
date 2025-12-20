package studio.one.application.mail.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import studio.one.application.mail.service.MailSyncNotifier;
import studio.one.application.mail.service.StompMailSyncNotifier;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService;

@AutoConfiguration
@EnableConfigurationProperties(MailFeatureProperties.class)
@ConditionalOnClass(name = "studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService")
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MailStompNotifierAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MailSyncNotifier.class)
    @org.springframework.context.annotation.Conditional(MailStompCondition.class)
    public MailSyncNotifier mailSyncNotifier(
            ObjectProvider<RealtimeMessagingService> messagingServiceProvider,
            MailFeatureProperties properties) {
        RealtimeMessagingService messagingService = messagingServiceProvider.getIfAvailable();
        if (messagingService == null) {
            throw new IllegalStateException(
                    "Mail notify transport is STOMP but RealtimeMessagingService is not available");
        }
        return new StompMailSyncNotifier(messagingService, properties);
    }

    static class MailStompCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            String prefix = PropertyKeys.Features.PREFIX + ".mail.web.";
            String sse = env.getProperty(prefix + "sse");
            if (sse != null) {
                return !Boolean.parseBoolean(sse);
            }
            String notify = env.getProperty(prefix + "notify");
            if (notify != null) {
                return "stomp".equalsIgnoreCase(notify);
            }
            return false;
        }
    }
}
