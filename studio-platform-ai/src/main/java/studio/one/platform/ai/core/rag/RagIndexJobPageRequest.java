package studio.one.platform.ai.core.rag;

public final class RagIndexJobPageRequest {

    private final int offset;
    private final int limit;

    public RagIndexJobPageRequest(
            int offset,
            int limit
    ) {
                offset = Math.max(0, offset);
                if (limit <= 0) {
                    limit = 50;
                }
                limit = Math.min(limit, 200);
        
        this.offset = offset;
        this.limit = limit;
    }

    public int offset() {
        return offset;
    }

    public int limit() {
        return limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagIndexJobPageRequest)) {
            return false;
        }
        RagIndexJobPageRequest that = (RagIndexJobPageRequest) o;
        return offset == that.offset
                && limit == that.limit;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(offset, limit);
    }

    @Override
    public String toString() {
        return "RagIndexJobPageRequest[" +
                "offset=" + offset + ", " +
                "limit=" + limit +
                "]";
    }

    public static RagIndexJobPageRequest defaults() {
        return new RagIndexJobPageRequest(0, 50);
    }
}
