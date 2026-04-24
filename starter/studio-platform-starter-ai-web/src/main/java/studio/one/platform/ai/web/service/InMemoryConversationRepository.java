package studio.one.platform.ai.web.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import studio.one.platform.ai.core.chat.ChatConversation;
import studio.one.platform.ai.core.chat.ChatConversationMessage;
import studio.one.platform.ai.core.chat.ChatConversationSummary;
import studio.one.platform.ai.core.chat.ConversationRepositoryPort;
import studio.one.platform.ai.core.chat.ConversationStatus;

/**
 * Single-node in-memory conversation repository for the web starter.
 */
public class InMemoryConversationRepository implements ConversationRepositoryPort {

    private final Map<String, ChatConversation> conversations = new LinkedHashMap<>();

    private final Map<String, List<ChatConversationMessage>> messages = new LinkedHashMap<>();

    @Override
    public synchronized ChatConversation saveConversation(ChatConversation conversation) {
        conversations.put(conversation.conversationId(), conversation);
        messages.computeIfAbsent(conversation.conversationId(), ignored -> new ArrayList<>());
        return conversation;
    }

    @Override
    public synchronized Optional<ChatConversation> findConversation(String conversationId) {
        ChatConversation conversation = conversations.get(conversationId);
        if (conversation == null || conversation.status() == ConversationStatus.DELETED) {
            return Optional.empty();
        }
        return Optional.of(conversation);
    }

    @Override
    public synchronized List<ChatConversationSummary> listConversations(String ownerId, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        return conversations.values().stream()
                .filter(conversation -> conversation.status() != ConversationStatus.DELETED)
                .filter(conversation -> conversation.ownerId().equals(ownerId == null ? "" : ownerId.trim()))
                .sorted(Comparator.comparing(ChatConversation::lastUpdatedAt).reversed())
                .skip(safeOffset)
                .limit(safeLimit)
                .map(ChatConversationSummary::from)
                .toList();
    }

    @Override
    public synchronized boolean deleteConversation(String conversationId) {
        ChatConversation existing = conversations.get(conversationId);
        if (existing == null || existing.status() == ConversationStatus.DELETED) {
            return false;
        }
        conversations.put(conversationId, copy(existing, existing.messageCount(), ConversationStatus.DELETED,
                existing.summary(), Instant.now()));
        return true;
    }

    @Override
    public synchronized ChatConversationMessage saveMessage(ChatConversationMessage message) {
        List<ChatConversationMessage> current = messages.computeIfAbsent(message.conversationId(), ignored -> new ArrayList<>());
        current.add(message);
        touch(message.conversationId(), null, ConversationStatus.ACTIVE);
        return message;
    }

    @Override
    public synchronized List<ChatConversationMessage> listMessages(String conversationId, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 100 : Math.min(limit, 500);
        return messages.getOrDefault(conversationId, List.of()).stream()
                .filter(ChatConversationMessage::active)
                .sorted(Comparator.comparing(ChatConversationMessage::createdAt))
                .skip(safeOffset)
                .limit(safeLimit)
                .toList();
    }

    @Override
    public synchronized boolean replaceAssistantResponse(
            String conversationId,
            String assistantMessageId,
            ChatConversationMessage replacement) {
        List<ChatConversationMessage> current = messages.get(conversationId);
        if (current == null) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            ChatConversationMessage existing = current.get(i);
            if (existing.messageId().equals(assistantMessageId)) {
                current.set(i, replacement);
                touch(conversationId, null, ConversationStatus.ACTIVE);
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean truncateAfter(String conversationId, String messageId) {
        List<ChatConversationMessage> current = messages.get(conversationId);
        if (current == null) {
            return false;
        }
        int index = -1;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).messageId().equals(messageId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return false;
        }
        current.subList(index + 1, current.size()).clear();
        touch(conversationId, null, ConversationStatus.ACTIVE);
        return true;
    }

    @Override
    public synchronized ChatConversation fork(String conversationId, String fromMessageId, String newConversationId) {
        if (conversations.containsKey(newConversationId)) {
            throw new IllegalArgumentException("Conversation already exists: " + newConversationId);
        }
        ChatConversation source = conversations.get(conversationId);
        if (source == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }
        Instant now = Instant.now();
        ChatConversation fork = new ChatConversation(
                newConversationId,
                source.ownerId(),
                source.title(),
                source.summary(),
                ConversationStatus.ACTIVE,
                conversationId,
                fromMessageId,
                0,
                now,
                now,
                Map.of());
        conversations.put(newConversationId, fork);
        List<ChatConversationMessage> copied = new ArrayList<>();
        boolean found = false;
        for (ChatConversationMessage message : messages.getOrDefault(conversationId, List.of())) {
            if (!message.active()) {
                continue;
            }
            copied.add(new ChatConversationMessage(
                    newConversationId + "-" + message.messageId(),
                    newConversationId,
                    message.message(),
                    message.parentMessageId(),
                    true,
                    message.createdAt(),
                    message.metadata()));
            if (message.messageId().equals(fromMessageId)) {
                found = true;
                break;
            }
        }
        if (!found) {
            conversations.remove(newConversationId);
            throw new IllegalArgumentException("Conversation message not found: " + fromMessageId);
        }
        messages.put(newConversationId, copied);
        touch(newConversationId, null, ConversationStatus.ACTIVE);
        return conversations.get(newConversationId);
    }

    @Override
    public synchronized ChatConversation compact(String conversationId, String summary) {
        ChatConversation existing = conversations.get(conversationId);
        if (existing == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }
        ChatConversation compacted = copy(existing, activeMessageCount(conversationId), ConversationStatus.COMPACTED,
                summary, Instant.now());
        conversations.put(conversationId, compacted);
        return compacted;
    }

    @Override
    public synchronized boolean cancel(String conversationId) {
        ChatConversation existing = conversations.get(conversationId);
        if (existing == null) {
            return false;
        }
        conversations.put(conversationId, copy(existing, activeMessageCount(conversationId), ConversationStatus.CANCELLED,
                existing.summary(), Instant.now()));
        return true;
    }

    private void touch(String conversationId, String summary, ConversationStatus status) {
        ChatConversation existing = conversations.get(conversationId);
        if (existing == null) {
            return;
        }
        conversations.put(conversationId, copy(existing, activeMessageCount(conversationId), status,
                summary == null ? existing.summary() : summary, Instant.now()));
    }

    private int activeMessageCount(String conversationId) {
        return (int) messages.getOrDefault(conversationId, List.of()).stream()
                .filter(ChatConversationMessage::active)
                .count();
    }

    private ChatConversation copy(
            ChatConversation conversation,
            int messageCount,
            ConversationStatus status,
            String summary,
            Instant lastUpdatedAt) {
        return new ChatConversation(
                conversation.conversationId(),
                conversation.ownerId(),
                conversation.title(),
                summary,
                status,
                conversation.parentConversationId(),
                conversation.forkedFromMessageId(),
                messageCount,
                conversation.createdAt(),
                lastUpdatedAt,
                conversation.metadata());
    }
}
