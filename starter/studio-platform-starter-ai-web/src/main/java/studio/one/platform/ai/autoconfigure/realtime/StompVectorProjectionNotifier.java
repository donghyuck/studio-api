package studio.one.platform.ai.autoconfigure.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.service.visualization.VectorProjectionNotifier;
import studio.one.platform.realtime.stomp.domain.model.RealtimeEnvelopes;
import studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService;

@Slf4j
@RequiredArgsConstructor
public class StompVectorProjectionNotifier implements VectorProjectionNotifier {

    private static final String DESTINATION_PREFIX = "/ai/vectors/projections/";

    private final RealtimeMessagingService messagingService;

    @Override
    public void notifyProjection(VectorProjection projection) {
        String destination = DESTINATION_PREFIX + projection.projectionId();
        messagingService.publish(RealtimeEnvelopes.toTopic(destination, VectorProjectionPayload.from(projection)));
        log.debug("[STOMP] Vector projection event sent to {}", destination);
    }
}
