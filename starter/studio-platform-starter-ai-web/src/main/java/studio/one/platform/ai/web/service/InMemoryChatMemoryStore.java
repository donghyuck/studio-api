package studio.one.platform.ai.web.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import studio.one.platform.ai.autoconfigure.AiWebChatProperties;
import studio.one.platform.ai.core.chat.ChatMemoryStore;
import studio.one.platform.ai.core.chat.ChatMessage;

/**
 * Bounded in-memory chat memory for a single application instance.
 */
public class InMemoryChatMemoryStore implements ChatMemoryStore {

    private final Cache<String, List<ChatMessage>> conversations;
    private final int maxMessages;

    public InMemoryChatMemoryStore(AiWebChatProperties.MemoryProperties properties) {
        if (properties.getMaxMessages() <= 0) {
            throw new IllegalArgumentException("maxMessages must be greater than 0");
        }
        if (properties.getMaxConversations() <= 0) {
            throw new IllegalArgumentException("maxConversations must be greater than 0");
        }
        if (properties.getTtl() == null || properties.getTtl().isNegative() || properties.getTtl().isZero()) {
            throw new IllegalArgumentException("ttl must be greater than 0");
        }
        this.maxMessages = properties.getMaxMessages();
        this.conversations = Caffeine.newBuilder()
                .maximumSize(properties.getMaxConversations())
                .expireAfterAccess(properties.getTtl())
                .build();
    }

    @Override
    public List<ChatMessage> get(String conversationId) {
        List<ChatMessage> messages = conversations.getIfPresent(conversationId);
        return messages == null ? Collections.emptyList() : List.copyOf(messages);
    }

    @Override
    public int append(String conversationId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return get(conversationId).size();
        }
        List<ChatMessage> updated = conversations.asMap().compute(conversationId, (key, existing) -> {
            List<ChatMessage> merged = new ArrayList<>();
            if (existing != null) {
                merged.addAll(existing);
            }
            merged.addAll(messages);
            int fromIndex = Math.max(0, merged.size() - maxMessages);
            return List.copyOf(merged.subList(fromIndex, merged.size()));
        });
        return updated.size();
    }
}
