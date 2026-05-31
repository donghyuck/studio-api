package studio.one.platform.autoconfigure.skillgraph.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.realtime.stomp.domain.model.RealtimeEnvelopes;
import studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphBatchJobNotifier;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;

@Slf4j
@RequiredArgsConstructor
public class StompSkillGraphBatchJobNotifier implements SkillGraphBatchJobNotifier {

    private static final String DESTINATION_PREFIX = "/skillgraph/jobs/";

    private final RealtimeMessagingService messagingService;

    @Override
    public void notifyJob(SkillGraphBatchJob job) {
        String destination = DESTINATION_PREFIX + job.jobId();
        messagingService.publish(RealtimeEnvelopes.toTopic(destination, SkillGraphBatchJobPayload.from(job)));
        log.debug("[STOMP] SkillGraph batch job event sent to {}", destination);
    }
}
