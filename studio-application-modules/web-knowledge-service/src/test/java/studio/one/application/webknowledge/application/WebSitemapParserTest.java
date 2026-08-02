package studio.one.application.webknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebSitemapParserTest {

    private final WebSitemapParser parser = new WebSitemapParser();

    @Test
    void parsesUrlSetAndNestedSitemapIndex() {
        var pages = parser.parse(bytes("""
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <url><loc>https://example.org/a</loc></url>
                  <url><loc>https://example.org/b</loc></url>
                </urlset>
                """));
        var nested = parser.parse(bytes("""
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <sitemap><loc>https://example.org/sitemap-1.xml</loc></sitemap>
                </sitemapindex>
                """));

        assertThat(pages.pageLocations())
                .containsExactly("https://example.org/a", "https://example.org/b");
        assertThat(pages.sitemapLocations()).isEmpty();
        assertThat(nested.sitemapLocations()).containsExactly("https://example.org/sitemap-1.xml");
    }

    @Test
    void rejectsDoctypeAndExternalEntityInput() {
        byte[] payload = bytes("""
                <!DOCTYPE urlset [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <url><loc>&xxe;</loc></url>
                </urlset>
                """);

        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SITEMAP_FORMAT_INVALID");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
