package studio.one.platform.ai.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "studio.ai.rag.answer-cache")
public class RagAnswerCacheProperties {

    public enum Type {
        NONE,
        CAFFEINE,
        REDIS
    }

    private Type type = Type.NONE;
    private Duration ttl = Duration.ofMinutes(15);
    private String namespace = "studio:ai:rag-answer:v1";
    private boolean failOpen = true;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type == null ? Type.NONE : type;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(15) : ttl;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        if (namespace != null && !namespace.isBlank()) {
            this.namespace = namespace.trim();
        }
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
}
