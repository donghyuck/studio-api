package studio.one.platform.ai.service.keyword;

import java.util.List;

/**
 * Extracts representative keywords from a piece of text.
 */
public interface KeywordExtractor {

    List<String> extract(String text);
}
