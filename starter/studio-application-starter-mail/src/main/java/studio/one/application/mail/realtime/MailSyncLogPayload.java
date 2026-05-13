package studio.one.application.mail.realtime;

import studio.one.application.mail.web.dto.response.MailSyncLogDto;

import studio.one.platform.realtime.stomp.domain.model.RealtimePayload;

public class MailSyncLogPayload implements RealtimePayload {

    private final MailSyncLogDto log;

    public MailSyncLogPayload(MailSyncLogDto log) {
        this.log = log;
    }

    public MailSyncLogDto log() {
        return log;
    }

    public MailSyncLogDto getLog() {
        return log;
    }
}
