package studio.one.platform.chunking.service;

import java.util.LinkedHashMap;
import java.util.Map;

public record BlockifyDocumentSignals(
        int articleHeadingCount,
        int chapterHeadingCount,
        int legalKeywordCount,
        int technicalKeywordCount,
        int manualKeywordCount,
        int codeFenceCount,
        int tableLineCount,
        int listLineCount,
        int paragraphCount,
        int longParagraphCount,
        int dialogueLineCount,
        double tableRatio,
        double listRatio,
        double longParagraphRatio,
        double dialogueRatio,
        int averageParagraphLength) {

    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("articleHeadingCount", articleHeadingCount);
        values.put("chapterHeadingCount", chapterHeadingCount);
        values.put("legalKeywordCount", legalKeywordCount);
        values.put("technicalKeywordCount", technicalKeywordCount);
        values.put("manualKeywordCount", manualKeywordCount);
        values.put("codeFenceCount", codeFenceCount);
        values.put("tableLineCount", tableLineCount);
        values.put("listLineCount", listLineCount);
        values.put("paragraphCount", paragraphCount);
        values.put("longParagraphCount", longParagraphCount);
        values.put("dialogueLineCount", dialogueLineCount);
        values.put("tableRatio", tableRatio);
        values.put("listRatio", listRatio);
        values.put("longParagraphRatio", longParagraphRatio);
        values.put("dialogueRatio", dialogueRatio);
        values.put("averageParagraphLength", averageParagraphLength);
        return values;
    }
}
