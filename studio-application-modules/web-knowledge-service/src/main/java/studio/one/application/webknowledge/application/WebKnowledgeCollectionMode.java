package studio.one.application.webknowledge.application;

public enum WebKnowledgeCollectionMode {
    SINGLE_PAGE,
    SITE;

    public static WebKnowledgeCollectionMode from(String value) {
        if (value == null || value.isBlank()) {
            return SINGLE_PAGE;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("WEB_COLLECTION_MODE_INVALID", ex);
        }
    }
}
