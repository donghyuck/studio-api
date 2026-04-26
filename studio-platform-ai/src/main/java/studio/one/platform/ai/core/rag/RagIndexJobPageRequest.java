package studio.one.platform.ai.core.rag;

public record RagIndexJobPageRequest(int offset, int limit) {

    public RagIndexJobPageRequest {
        offset = Math.max(0, offset);
        if (limit <= 0) {
            limit = 50;
        }
        limit = Math.min(limit, 200);
    }

    public static RagIndexJobPageRequest defaults() {
        return new RagIndexJobPageRequest(0, 50);
    }
}
