package studio.one.platform.ai.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.AI.Endpoints.PREFIX + ".chat")
public class AiWebChatProperties {

    private final MemoryProperties memory = new MemoryProperties();

    public MemoryProperties getMemory() {
        return memory;
    }

    public static class MemoryProperties {
        private boolean enabled = false;
        private int maxMessages = 20;
        private long maxConversations = 1_000;
        private Duration ttl = Duration.ofMinutes(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        public long getMaxConversations() {
            return maxConversations;
        }

        public void setMaxConversations(long maxConversations) {
            this.maxConversations = maxConversations;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }
}
