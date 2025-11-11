package studio.one.platform.ai.core.chat;

/**
 * Contract for interacting with a chat-capable AI provider.
 */
public interface ChatPort {

    ChatResponse chat(ChatRequest request);
}
