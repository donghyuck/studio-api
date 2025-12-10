package studio.one.application.mail.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;
import studio.one.application.mail.web.dto.MailSyncLogDto;

@Slf4j
public class MailSyncNotifier {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter register(long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void notify(MailSyncLogDto dto) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("mail-sync").data(dto));
            } catch (IOException ex) {
                emitters.remove(emitter);
                log.debug("Removed SSE emitter due to send failure: {}", ex.getMessage());
            }
        }
    }
}
