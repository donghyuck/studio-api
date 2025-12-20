package studio.one.application.mail.realtime;

import studio.one.application.mail.web.dto.MailSyncLogDto;

import studio.one.platform.realtime.stomp.domain.model.RealtimePayload;

public record MailSyncLogPayload(MailSyncLogDto log) implements RealtimePayload {

}
