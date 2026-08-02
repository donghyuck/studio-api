package studio.one.application.webknowledge.infrastructure.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jsoup.Jsoup;

import studio.one.application.webknowledge.application.WebSiteDiscoveryPort;

public final class JsoupWebSiteDiscovery implements WebSiteDiscoveryPort {

    @Override
    public List<String> links(byte[] html, URI pageUri) {
        if (html == null || html.length == 0 || pageUri == null) {
            return List.of();
        }
        return Jsoup.parse(new String(html, StandardCharsets.UTF_8), pageUri.toString())
                .select("main a[href], article a[href], body a[href]")
                .stream()
                .map(element -> element.attr("abs:href"))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
