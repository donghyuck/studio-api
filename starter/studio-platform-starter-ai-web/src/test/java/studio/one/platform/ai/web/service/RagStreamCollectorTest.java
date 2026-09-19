package studio.one.platform.ai.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import studio.one.platform.ai.core.chat.ChatStreamEvent;

class RagStreamCollectorTest {
    private final RagStreamCollector collector = new RagStreamCollector();

    @Test
    void collectsDeltasAndClosesStreamAtComplete() {
        AtomicBoolean closed = new AtomicBoolean();
        var result = collector.collect(Stream.of(ChatStreamEvent.delta("first", "model", null),
                ChatStreamEvent.delta(" second", "model", null), ChatStreamEvent.complete("model", null),
                ChatStreamEvent.delta("ignored", "model", null)).onClose(() -> closed.set(true)));
        assertThat(result.content()).isEqualTo("first second");
        assertThat(result.shouldComplete()).isTrue();
        assertThat(result.terminal().model()).isEqualTo("model");
        assertThat(closed).isTrue();
    }

    @Test
    void providerErrorDiscardsUnvalidatedDraftAndPrivateErrorMessage() {
        var result = collector.collect(Stream.of(ChatStreamEvent.delta("draft", "m", null),
                ChatStreamEvent.error("private provider detail", null)));
        assertThat(result.content()).isEmpty();
        assertThat(result.errorCode()).isEqualTo("PROVIDER_STREAM_FAILED");
        assertThat(result.shouldComplete()).isFalse();
    }

    @Test
    void supportsExhaustionWithTextButDoesNotCompleteEmptyStream() {
        assertThat(collector.collect(Stream.of(ChatStreamEvent.delta("text", "m", null))).shouldComplete()).isTrue();
        assertThat(collector.collect(Stream.empty()).shouldComplete()).isFalse();
    }

    @Test
    void iteratorFailureClosesStreamAndPropagatesToSanitizingAdapter() {
        AtomicBoolean closed = new AtomicBoolean();
        Stream<ChatStreamEvent> stream = Stream.<ChatStreamEvent>generate(() -> { throw new IllegalStateException("failed"); })
                .onClose(() -> closed.set(true));
        assertThatThrownBy(() -> collector.collect(stream)).isInstanceOf(IllegalStateException.class);
        assertThat(closed).isTrue();
    }

    @Test
    void closeFailurePreventsCompletedResultFromReachingFinalization() {
        var stream = Stream.of(ChatStreamEvent.delta("draft", "m", null), ChatStreamEvent.complete("m", null))
                .onClose(() -> { throw new IllegalStateException("close failure"); });
        assertThatThrownBy(() -> collector.collect(stream)).isInstanceOf(IllegalStateException.class);
    }
}
