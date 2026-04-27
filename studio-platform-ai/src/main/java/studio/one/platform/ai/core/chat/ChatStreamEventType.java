package studio.one.platform.ai.core.chat;

/**
 * Provider-neutral chat stream event type.
 */
public enum ChatStreamEventType {
    DELTA("delta"),
    USAGE("usage"),
    COMPLETE("complete"),
    ERROR("error");

    private final String value;

    ChatStreamEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
