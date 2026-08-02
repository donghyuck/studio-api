package studio.one.application.webknowledge.application;

record WebKnowledgeCrawlProgressSnapshot(
        int discovered,
        int fetched,
        int indexed,
        int unchanged,
        int updated,
        int removed,
        int failed,
        int skipped,
        long responseBytes,
        long normalizedChars) {
}
