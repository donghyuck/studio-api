package studio.one.application.wiki.application.usecase;

public interface WikiRenderService {

    String toSanitizedHtml(String markdown);
}
