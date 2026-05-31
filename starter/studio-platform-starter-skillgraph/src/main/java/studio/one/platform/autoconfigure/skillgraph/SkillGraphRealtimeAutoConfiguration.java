package studio.one.platform.autoconfigure.skillgraph;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import studio.one.platform.autoconfigure.skillgraph.realtime.StompSkillGraphBatchJobNotifier;
import studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphBatchJobNotifier;

@AutoConfiguration(after = SkillGraphAutoConfiguration.class)
@ConditionalOnClass(name = "studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService")
@ConditionalOnProperty(prefix = "studio.features.skillgraph.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SkillGraphRealtimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SkillGraphBatchJobNotifier.class)
    public SkillGraphBatchJobNotifier skillGraphBatchJobNotifier(
            ObjectProvider<RealtimeMessagingService> messagingServiceProvider) {
        RealtimeMessagingService messagingService = messagingServiceProvider.getIfAvailable();
        if (messagingService == null) {
            return SkillGraphBatchJobNotifier.NOOP;
        }
        return new StompSkillGraphBatchJobNotifier(messagingService);
    }
}
