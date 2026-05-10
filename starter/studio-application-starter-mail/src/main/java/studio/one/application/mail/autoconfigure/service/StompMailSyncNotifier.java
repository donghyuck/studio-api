package studio.one.application.mail.autoconfigure.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.mail.autoconfigure.MailFeatureProperties;
import studio.one.application.mail.application.usecase.MailSyncNotifier;
import studio.one.application.mail.domain.model.MailSyncLog;
import studio.one.application.mail.realtime.MailSyncLogPayload;
import studio.one.application.mail.web.dto.response.MailSyncLogDto;
@Slf4j
@RequiredArgsConstructor
public class StompMailSyncNotifier implements MailSyncNotifier {

    private final studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService messagingService;
    private final MailFeatureProperties properties;

    @Override
    public void notifyLog(MailSyncLog syncLog) {
        String destination = properties.getWeb().getStompDestination();
        messagingService.sendToTopic(destination, new MailSyncLogPayload(MailSyncLogDto.from(syncLog)));
        log.info("[STOMP] Mail sync event sent to {}", destination);
    }
}
