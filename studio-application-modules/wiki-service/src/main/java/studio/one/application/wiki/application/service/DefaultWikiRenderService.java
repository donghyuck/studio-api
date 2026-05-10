package studio.one.application.wiki.application.service;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import studio.one.application.wiki.application.usecase.WikiRenderService;

public class DefaultWikiRenderService implements WikiRenderService {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();
    private final Safelist safelist = Safelist.relaxed()
            .addProtocols("a", "href", "http", "https", "mailto");

    @Override
    public String toSanitizedHtml(String markdown) {
        String source = markdown == null ? "" : markdown;
        String html = renderer.render(parser.parse(source));
        return Jsoup.clean(html, safelist);
    }
}
