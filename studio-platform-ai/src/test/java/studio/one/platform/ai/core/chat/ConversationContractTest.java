package studio.one.platform.ai.core.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConversationContractTest {

    @Test
    void conversationSummaryUsesStableListShape() {
        ChatConversation conversation = new ChatConversation(
                "conv-1",
                "user-1",
                "Title",
                "Summary",
                ConversationStatus.ACTIVE,
                "",
                "",
                3,
                Instant.parse("2026-04-24T00:00:00Z"),
                Instant.parse("2026-04-24T00:01:00Z"),
                Map.of("ownerId", "user-1"));

        ChatConversationSummary summary = ChatConversationSummary.from(conversation);

        assertThat(summary.conversationId()).isEqualTo("conv-1");
        assertThat(summary.ownerId()).isEqualTo("user-1");
        assertThat(summary.title()).isEqualTo("Title");
        assertThat(summary.summary()).isEqualTo("Summary");
        assertThat(summary.messageCount()).isEqualTo(3);
        assertThat(summary.status()).isEqualTo(ConversationStatus.ACTIVE);
    }

    @Test
    void conversationMessageRequiresStableIdsAndMessage() {
        assertThatThrownBy(() -> new ChatConversationMessage(
                "",
                "conv-1",
                ChatMessage.user("hello"),
                "",
                true,
                Instant.now(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId");
    }
}
