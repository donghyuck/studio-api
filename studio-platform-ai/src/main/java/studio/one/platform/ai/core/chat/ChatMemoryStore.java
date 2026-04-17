package studio.one.platform.ai.core.chat;

import java.util.List;

/**
 * Stores recent chat messages for a client-managed conversation.
 */
public interface ChatMemoryStore {

    List<ChatMessage> get(String conversationId);

    int append(String conversationId, List<ChatMessage> messages);
}
