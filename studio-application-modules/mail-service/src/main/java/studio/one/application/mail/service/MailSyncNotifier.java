package studio.one.application.mail.service;

import java.util.ArrayList;
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

        log.info("[SSE] Mail sync listener registered. total={}", emitters.size());

        return emitter;
    }

    public void notifyLog(MailSyncLogDto dto) {
        List<SseEmitter> dead = new ArrayList<>();

        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("mail-sync")
                        .data(dto));
            } catch (Exception e) {
                dead.add(emitter);
            }
        });

        emitters.removeAll(dead);

        log.info("[SSE] Mail sync event sent. alive={}, dead={}",
                emitters.size(), dead.size());
    }
}