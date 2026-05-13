package studio.one.platform.ai.core.rag;

import java.util.List;

public final class RagIndexJobPage {

    private final List<RagIndexJob> jobs;
    private final long total;
    private final int offset;
    private final int limit;

    public RagIndexJobPage(
            List<RagIndexJob> jobs,
            long total,
            int offset,
            int limit
    ) {
                jobs = jobs == null ? List.of() : List.copyOf(jobs);
                total = Math.max(0L, total);
                offset = Math.max(0, offset);
                limit = Math.max(0, limit);
        
        this.jobs = jobs;
        this.total = total;
        this.offset = offset;
        this.limit = limit;
    }

    public List<RagIndexJob> jobs() {
        return jobs;
    }

    public long total() {
        return total;
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
        if (!(o instanceof RagIndexJobPage)) {
            return false;
        }
        RagIndexJobPage that = (RagIndexJobPage) o;
        return java.util.Objects.equals(jobs, that.jobs)
                && total == that.total
                && offset == that.offset
                && limit == that.limit;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(jobs, total, offset, limit);
    }

    @Override
    public String toString() {
        return "RagIndexJobPage[" +
                "jobs=" + jobs + ", " +
                "total=" + total + ", " +
                "offset=" + offset + ", " +
                "limit=" + limit +
                "]";
    }
}
