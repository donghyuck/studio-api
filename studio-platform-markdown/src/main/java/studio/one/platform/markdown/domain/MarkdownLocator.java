package studio.one.platform.markdown.domain;

public record MarkdownLocator(
        String locatorId,
        String revisionId,
        String locatorType,
        Integer locatorNo,
        String title,
        int startOffset,
        int endOffset,
        String sourceRef,
        String metadataJson,
        Integer page,
        Integer slide,
        Object bbox) {

    public MarkdownLocator(
            String locatorId,
            String revisionId,
            String locatorType,
            Integer locatorNo,
            String title,
            int startOffset,
            int endOffset,
            String sourceRef,
            String metadataJson) {
        this(locatorId, revisionId, locatorType, locatorNo, title, startOffset, endOffset, sourceRef, metadataJson,
                page(locatorType, locatorNo), slide(locatorType, locatorNo), null);
    }

    private static Integer page(String locatorType, Integer locatorNo) {
        return "page".equalsIgnoreCase(locatorType) || "NORMALIZED_BLOCK".equalsIgnoreCase(locatorType)
                ? locatorNo
                : null;
    }

    private static Integer slide(String locatorType, Integer locatorNo) {
        return "slide".equalsIgnoreCase(locatorType) ? locatorNo : null;
    }
}
