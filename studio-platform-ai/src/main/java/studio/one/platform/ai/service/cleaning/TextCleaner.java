package studio.one.platform.ai.service.cleaning;

/**
 * Cleans extracted document text before it is split into RAG chunks.
 */
public interface TextCleaner {

    TextCleaningResult clean(String text);
}
