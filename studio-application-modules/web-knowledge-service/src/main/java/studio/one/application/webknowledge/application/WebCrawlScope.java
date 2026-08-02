package studio.one.application.webknowledge.application;

public enum WebCrawlScope {
    PATH_PREFIX,
    SAME_ORIGIN;

    static WebCrawlScope from(String value) {
        if (value == null || value.isBlank()) {
            return PATH_PREFIX;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("WEB_CRAWL_SCOPE_INVALID", ex);
        }
    }
}
