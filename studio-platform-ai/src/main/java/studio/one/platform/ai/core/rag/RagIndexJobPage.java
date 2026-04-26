package studio.one.platform.ai.core.rag;

import java.util.List;

public record RagIndexJobPage(
        List<RagIndexJob> jobs,
        long total,
        int offset,
        int limit) {

    public RagIndexJobPage {
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
        total = Math.max(0L, total);
        offset = Math.max(0, offset);
        limit = Math.max(0, limit);
    }
}
