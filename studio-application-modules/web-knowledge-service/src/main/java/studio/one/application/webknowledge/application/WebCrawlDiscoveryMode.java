package studio.one.application.webknowledge.application;

public enum WebCrawlDiscoveryMode {
    LINKS_ONLY,
    SITEMAP_ONLY,
    SITEMAP_AND_LINKS;

    static WebCrawlDiscoveryMode from(String value) {
        if (value == null || value.isBlank()) {
            return SITEMAP_AND_LINKS;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("WEB_CRAWL_DISCOVERY_MODE_INVALID", ex);
        }
    }
}
