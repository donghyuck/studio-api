package studio.one.platform.ai.web.service;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.chat.ChatConversation;
import studio.one.platform.ai.core.chat.ChatConversationMessage;
import studio.one.platform.ai.core.chat.ChatConversationSummary;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.chat.ConversationRepositoryPort;
import studio.one.platform.ai.core.chat.ConversationStatus;
import studio.one.platform.ai.web.dto.ConversationDetailDto;
import studio.one.platform.ai.web.dto.ConversationMessageDto;
import studio.one.platform.ai.web.dto.ConversationSummaryDto;

public class ConversationChatService {

    private static final String OWNER_ANONYMOUS = "anonymous";

    private static final int MESSAGE_PAGE_SIZE = 500;

    private final ConversationRepositoryPort repository;

    public ConversationChatService(ConversationRepositoryPort repository) {
        this.repository = repository;
    }

    public String ownerId(Principal principal) {
        if (principal == null) {
            return OWNER_ANONYMOUS;
        }
        String name = normalize(principal.getName());
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Principal name is required for conversation APIs");
        }
        return "principal:" + name;
    }

    public List<ConversationSummaryDto> list(String ownerId, int offset, int limit) {
        return repository.listConversations(ownerId, offset, limit).stream()
                .map(summary -> new ConversationSummaryDto(
                        clientConversationId(ownerId, summary.conversationId()),
                        summary.title(),
                        summary.summary(),
                        summary.messageCount(),
                        summary.lastUpdatedAt(),
                        summary.status().name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public ConversationDetailDto detail(String ownerId, String conversationId) {
        ChatConversation conversation = requireConversation(ownerId, conversationId);
        List<ConversationMessageDto> messages = allMessages(conversation.conversationId()).stream()
                .map(this::toDto)
                .toList();
        return new ConversationDetailDto(
                clientConversationId(ownerId, conversation.conversationId()),
                conversation.title(),
                conversation.summary(),
                conversation.status().name().toLowerCase(Locale.ROOT),
                clientConversationId(ownerId, conversation.parentConversationId()),
                conversation.forkedFromMessageId(),
                conversation.messageCount(),
                conversation.createdAt(),
                conversation.lastUpdatedAt(),
                conversation.metadata(),
                messages);
    }

    public boolean delete(String ownerId, String conversationId) {
        ChatConversation conversation = requireConversation(ownerId, conversationId);
        return repository.deleteConversation(conversation.conversationId());
    }

    public ChatConversation ensureConversation(String ownerId, String conversationId, List<ChatMessage> seedMessages) {
        String storageId = storageConversationId(ownerId, conversationId);
        return repository.findConversation(storageId).orElseGet(() -> repository.saveConversation(new ChatConversation(
                storageId,
                ownerId,
                title(seedMessages),
                summary(seedMessages),
                ConversationStatus.ACTIVE,
                "",
                "",
                0,
                Instant.now(),
                Instant.now(),
                Map.of())));
    }

    public int appendTurn(
            String ownerId,
            String conversationId,
            List<ChatMessage> requestMessages,
            ChatResponse response) {
        ChatConversation conversation = ensureConversation(ownerId, conversationId, requestMessages);
        List<ChatMessage> stored = new ArrayList<>();
        requestMessages.stream()
                .filter(message -> message.role() != ChatMessageRole.SYSTEM)
                .forEach(stored::add);
        response.messages().stream()
                .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                .forEach(stored::add);
        for (ChatMessage message : stored) {
            repository.saveMessage(new ChatConversationMessage(
                    UUID.randomUUID().toString(),
                    conversation.conversationId(),
                    message,
                    "",
                    true,
                    Instant.now(),
                    Map.of()));
        }
        return conversation.messageCount() + stored.size();
    }

    public List<ChatConversationMessage> messagesForRegenerate(String ownerId, String conversationId) {
        ChatConversation conversation = requireConversation(ownerId, conversationId);
        List<ChatConversationMessage> messages = allMessages(conversation.conversationId());
        int lastUser = lastMessageIndex(messages, ChatMessageRole.USER);
        if (lastUser < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No user message available for regenerate");
        }
        return messages.subList(0, lastUser + 1);
    }

    public int replaceLastAssistantResponse(String ownerId, String conversationId, ChatResponse response) {
        ChatConversation conversation = requireConversation(ownerId, conversationId);
        List<ChatConversationMessage> messages = allMessages(conversation.conversationId());
        int assistantAfterLastUser = assistantAfterLastUser(messages);
        ChatConversationMessage replacement = new ChatConversationMessage(
                UUID.randomUUID().toString(),
                conversation.conversationId(),
                response.messages().stream()
                        .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Provider response does not include assistant message")),
                "",
                true,
                Instant.now(),
                response.metadata());
        if (assistantAfterLastUser >= 0) {
            repository.replaceAssistantResponse(
                    conversation.conversationId(),
                    messages.get(assistantAfterLastUser).messageId(),
                    replacement);
            return conversation.messageCount();
        } else {
            repository.saveMessage(replacement);
            return conversation.messageCount() + 1;
        }
    }

    public ConversationDetailDto truncate(String ownerId, String conversationId, String messageId) {
        ChatConversation conversation = requireConversation(ownerId, conversationId);
        if (!repository.truncateAfter(conversation.conversationId(), requireText(messageId, "messageId"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation message not found");
        }
        return detail(ownerId, conversationId);
    }

    public ConversationDetailDto fork(
            String ownerId,
            String conversationId,
            String messageId,
            String newConversationId) {
        ChatConversation conversation = requireConversation(ownerId, conversationId);
        String newId = normalize(newConversationId);
        if (newId == null) {
            newId = UUID.randomUUID().toString();
        }
        try {
            repository.fork(conversation.conversationId(), requireText(messageId, "messageId"),
                    storageConversationId(ownerId, newId));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        return detail(ownerId, newId);
    }

    public ConversationDetailDto compact(String ownerId, String conversationId, String summary) {
        ChatConversation conversation = requireConversation(ownerId, conversationId);
        repository.compact(conversation.conversationId(), requireText(summary, "summary"));
        return detail(ownerId, conversationId);
    }

    public ConversationDetailDto cancel(String ownerId, String conversationId) {
        ChatConversation conversation = requireConversation(ownerId, conversationId);
        if (!repository.cancel(conversation.conversationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        return detail(ownerId, conversationId);
    }

    private ChatConversation requireConversation(String ownerId, String conversationId) {
        String storageId = storageConversationId(ownerId, requireText(conversationId, "conversationId"));
        ChatConversation conversation = repository.findConversation(storageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.ownerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        return conversation;
    }

    private ConversationMessageDto toDto(ChatConversationMessage message) {
        return new ConversationMessageDto(
                message.messageId(),
                message.message().role().name().toLowerCase(Locale.ROOT),
                message.message().content(),
                message.createdAt(),
                message.metadata());
    }

    private List<ChatConversationMessage> allMessages(String conversationId) {
        List<ChatConversationMessage> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<ChatConversationMessage> page = repository.listMessages(conversationId, offset, MESSAGE_PAGE_SIZE);
            if (page.isEmpty()) {
                return List.copyOf(all);
            }
            all.addAll(page);
            if (page.size() < MESSAGE_PAGE_SIZE) {
                return List.copyOf(all);
            }
            offset += page.size();
        }
    }

    private int lastMessageIndex(List<ChatConversationMessage> messages, ChatMessageRole role) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).message().role() == role) {
                return i;
            }
        }
        return -1;
    }

    private int assistantAfterLastUser(List<ChatConversationMessage> messages) {
        int lastUser = lastMessageIndex(messages, ChatMessageRole.USER);
        if (lastUser < 0) {
            return -1;
        }
        for (int i = lastUser + 1; i < messages.size(); i++) {
            if (messages.get(i).message().role() == ChatMessageRole.ASSISTANT) {
                return i;
            }
        }
        return -1;
    }

    private String title(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.role() == ChatMessageRole.USER)
                .map(ChatMessage::content)
                .map(this::normalize)
                .filter(value -> value != null)
                .findFirst()
                .map(value -> value.length() <= 40 ? value : value.substring(0, 40))
                .orElse("Conversation");
    }

    private String summary(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.role() == ChatMessageRole.USER)
                .map(ChatMessage::content)
                .map(this::normalize)
                .filter(value -> value != null)
                .findFirst()
                .orElse("");
    }

    private String storageConversationId(String ownerId, String conversationId) {
        return ownerId + ":" + requireText(conversationId, "conversationId");
    }

    private String clientConversationId(String ownerId, String storageConversationId) {
        String normalized = normalize(storageConversationId);
        if (normalized == null) {
            return "";
        }
        String prefix = ownerId + ":";
        return normalized.startsWith(prefix) ? normalized.substring(prefix.length()) : normalized;
    }

    private String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
