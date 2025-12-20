package studio.one.application.mail.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.mail.autoconfigure.MailFeatureProperties;
import studio.one.application.mail.realtime.MailSyncLogPayload;
import studio.one.application.mail.web.dto.MailSyncLogDto;

import studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService;

@Slf4j
@RequiredArgsConstructor
public class StompMailSyncNotifier implements MailSyncNotifier {

    private final RealtimeMessagingService messagingService;
    private final MailFeatureProperties properties;

    @Override
    public void notifyLog(MailSyncLogDto dto) {
        String destination = properties.getWeb().getStompDestination();
        messagingService.sendToTopic(destination, new MailSyncLogPayload(dto));
        log.info("[STOMP] Mail sync event sent to {}", destination);
    }
}
