package studio.one.platform.ai.web.service;

import java.util.Iterator;
import java.util.stream.Stream;
import studio.one.platform.ai.core.chat.ChatStreamEvent;
import studio.one.platform.ai.core.chat.ChatStreamEventType;

/** Buffers provider deltas; only the caller's validated canonical answer may be published. */
public final class RagStreamCollector {
    public Collected collect(Stream<ChatStreamEvent> events) {
        StringBuilder assistant = new StringBuilder();
        ChatStreamEvent last = null;
        try (events) {
            Iterator<ChatStreamEvent> iterator = events.iterator();
            while (iterator.hasNext()) {
                ChatStreamEvent event = iterator.next();
                last = event;
                if (event.type() == ChatStreamEventType.ERROR) {
                    return new Collected("", null, "PROVIDER_STREAM_FAILED", false);
                }
                if (event.type() == ChatStreamEventType.DELTA) {
                    assistant.append(event.delta());
                } else if (event.type() == ChatStreamEventType.COMPLETE) {
                    return new Collected(assistant.toString(), event, null, true);
                }
            }
        }
        return new Collected(assistant.toString(), last, null, !assistant.isEmpty());
    }

    public record Collected(String content, ChatStreamEvent terminal, String errorCode, boolean shouldComplete) { }
}
