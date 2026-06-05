package studio.one.platform.autoconfigure.skillgraph.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.realtime.stomp.domain.model.RealtimeEnvelopes;
import studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobNotifier;

@Slf4j
@RequiredArgsConstructor
public class StompSkillRagExtractionJobNotifier implements SkillRagExtractionJobNotifier {

    private static final String DESTINATION = "/skillgraph/extraction-jobs";

    private final RealtimeMessagingService messagingService;

    @Override
    public void notifyJob(SkillRagExtractionJob job) {
        SkillRagExtractionJobPayload payload = SkillRagExtractionJobPayload.from(job);
        messagingService.publish(RealtimeEnvelopes.toTopic(DESTINATION, payload));
        messagingService.publish(RealtimeEnvelopes.toTopic(DESTINATION + "/" + job.jobId(), payload));
        log.debug("[STOMP] SkillGraph extraction job event sent for {}", job.jobId());
    }
}
