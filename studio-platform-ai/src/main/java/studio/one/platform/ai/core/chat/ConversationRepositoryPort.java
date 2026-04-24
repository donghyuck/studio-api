package studio.one.platform.ai.core.chat;

import java.util.List;
import java.util.Optional;

/**
 * Provider-neutral persistence port for chat conversations.
 */
public interface ConversationRepositoryPort {

    ChatConversation saveConversation(ChatConversation conversation);

    Optional<ChatConversation> findConversation(String conversationId);

    List<ChatConversationSummary> listConversations(String ownerId, int offset, int limit);

    boolean deleteConversation(String conversationId);

    ChatConversationMessage saveMessage(ChatConversationMessage message);

    List<ChatConversationMessage> listMessages(String conversationId);

    boolean replaceAssistantResponse(String conversationId, String assistantMessageId, ChatConversationMessage replacement);

    boolean truncateAfter(String conversationId, String messageId);

    ChatConversation fork(String conversationId, String fromMessageId, String newConversationId);

    ChatConversation compact(String conversationId, String summary);

    boolean cancel(String conversationId);
}
