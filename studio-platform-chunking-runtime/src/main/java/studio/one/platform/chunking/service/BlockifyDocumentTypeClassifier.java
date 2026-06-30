package studio.one.platform.chunking.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

public class BlockifyDocumentTypeClassifier {

    static final String KEY_BLOCKIFY_DOCUMENT_TYPE = "blockifyDocumentType";

    private static final Pattern ARTICLE_HEADING = Pattern.compile(".*제\\s*\\d+조\\s*(?:\\([^)]*\\))?.*");
    private static final Pattern CHAPTER_HEADING = Pattern.compile("(?i).*\\bchapter\\s+\\w+\\b.*");
    private static final Pattern LIST_LINE = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+|[①-⑳]\\s*|[가-하][.)]\\s+).+");
    private static final List<String> LEGAL_KEYWORDS = List.of(
            "규정", "취업규칙", "근로자", "사원", "회사", "휴가", "징계", "급여", "임금", "복무", "제재", "승인", "신청");
    private static final List<String> TECHNICAL_KEYWORDS = List.of(
            "api", "endpoint", "request", "response", "http", "docker", "gradle", "exception", "configuration", "schema");
    private static final List<String> MANUAL_KEYWORDS = List.of(
            "절차", "방법", "단계", "사용", "설정", "관리자", "사용자", "메뉴", "화면", "입력", "선택");
    private static final List<String> DIALOGUE_MARKERS = List.of("\"", "“", "”", "said", "asked", "replied", "cried", "told");

    public BlockifyDocumentTypeClassification classify(NormalizedDocument document, ChunkingContext context) {
        BlockifyDocumentType requested = requestedType(document, context);
        BlockifyDocumentSignals signals = signals(document);
        if (requested != BlockifyDocumentType.AUTO) {
            return new BlockifyDocumentTypeClassification(requested, requested, 1.0d, signals, "override");
        }
        Score policy = new Score(BlockifyDocumentType.POLICY,
                signals.articleHeadingCount() * 5.0d
                        + signals.legalKeywordCount() * 1.5d
                        + signals.tableRatio() * 2.0d);
        Score narrative = new Score(BlockifyDocumentType.NARRATIVE,
                signals.chapterHeadingCount() * 4.0d
                        + signals.dialogueRatio() * 6.0d
                        + signals.longParagraphRatio() * 3.0d);
        Score technical = new Score(BlockifyDocumentType.TECHNICAL,
                signals.codeFenceCount() * 4.0d
                        + signals.technicalKeywordCount() * 2.0d);
        Score manual = new Score(BlockifyDocumentType.MANUAL,
                signals.manualKeywordCount() * 1.5d
                        + signals.listRatio() * 3.0d);
        Score table = new Score(BlockifyDocumentType.TABLE_HEAVY,
                signals.tableRatio() * 10.0d
                        + signals.tableLineCount() * 0.5d);
        Score best = List.of(policy, narrative, technical, manual, table).stream()
                .max(java.util.Comparator.comparingDouble(Score::value))
                .orElse(new Score(BlockifyDocumentType.GENERAL, 0.0d));
        if (best.value() < 2.0d) {
            return new BlockifyDocumentTypeClassification(BlockifyDocumentType.AUTO, BlockifyDocumentType.GENERAL,
                    0.5d, signals, "low-signal");
        }
        double total = policy.value() + narrative.value() + technical.value() + manual.value() + table.value();
        double confidence = total <= 0.0d ? 0.5d : Math.min(0.99d, Math.max(0.5d, best.value() / total));
        return new BlockifyDocumentTypeClassification(BlockifyDocumentType.AUTO, best.type(), confidence,
                signals, "rule-score");
    }

    private BlockifyDocumentType requestedType(NormalizedDocument document, ChunkingContext context) {
        String value = firstNonBlank(metadata(context == null ? null : context.metadata()),
                metadata(document == null ? null : document.metadata()));
        return BlockifyDocumentType.from(value);
    }

    private String metadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(KEY_BLOCKIFY_DOCUMENT_TYPE);
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private BlockifyDocumentSignals signals(NormalizedDocument document) {
        List<NormalizedBlock> blocks = document == null || document.blocks() == null ? List.of() : document.blocks();
        String text = document == null ? "" : document.chunkableText();
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int articleHeadingCount = 0;
        int chapterHeadingCount = 0;
        int tableLineCount = 0;
        int listLineCount = 0;
        int dialogueLineCount = 0;
        int paragraphCount = 0;
        int longParagraphCount = 0;
        int paragraphChars = 0;
        for (NormalizedBlock block : blocks) {
            if (block == null || block.text() == null || block.text().isBlank()) {
                continue;
            }
            String value = block.text().trim();
            if (ARTICLE_HEADING.matcher(value).matches()) {
                articleHeadingCount++;
            }
            if (CHAPTER_HEADING.matcher(value).matches()) {
                chapterHeadingCount++;
            }
            for (String line : value.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                    tableLineCount++;
                }
                if (LIST_LINE.matcher(trimmed).matches()) {
                    listLineCount++;
                }
                String lineLower = trimmed.toLowerCase(Locale.ROOT);
                if (DIALOGUE_MARKERS.stream().anyMatch(lineLower::contains)) {
                    dialogueLineCount++;
                }
            }
            if (block.type() == NormalizedBlockType.PARAGRAPH || block.type() == NormalizedBlockType.DOCUMENT) {
                paragraphCount++;
                paragraphChars += value.length();
                if (value.length() >= 300) {
                    longParagraphCount++;
                }
            }
        }
        int lineCount = Math.max(1, (int) (text == null || text.isBlank() ? 1 : text.lines().count()));
        int legalKeywordCount = keywordCount(lower, LEGAL_KEYWORDS);
        int technicalKeywordCount = keywordCount(lower, TECHNICAL_KEYWORDS);
        int manualKeywordCount = keywordCount(lower, MANUAL_KEYWORDS);
        int codeFenceCount = count(lower, "```");
        int averageParagraphLength = paragraphCount == 0 ? 0 : paragraphChars / paragraphCount;
        return new BlockifyDocumentSignals(
                articleHeadingCount,
                chapterHeadingCount,
                legalKeywordCount,
                technicalKeywordCount,
                manualKeywordCount,
                codeFenceCount,
                tableLineCount,
                listLineCount,
                paragraphCount,
                longParagraphCount,
                dialogueLineCount,
                tableLineCount / (double) lineCount,
                listLineCount / (double) lineCount,
                paragraphCount == 0 ? 0.0d : longParagraphCount / (double) paragraphCount,
                dialogueLineCount / (double) lineCount,
                averageParagraphLength);
    }

    private int keywordCount(String text, List<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String keyword : keywords) {
            count += countKeyword(text, keyword.toLowerCase(Locale.ROOT));
        }
        return count;
    }

    private int countKeyword(String text, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return 0;
        }
        if (keyword.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '-')) {
            Pattern pattern = Pattern.compile("(?<![\\p{Alnum}_-])" + Pattern.quote(keyword) + "(?![\\p{Alnum}_-])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            int matches = 0;
            var matcher = pattern.matcher(text);
            while (matcher.find()) {
                matches++;
            }
            return matches;
        }
        return count(text, keyword);
    }

    private int count(String text, String token) {
        if (text == null || text.isBlank() || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private record Score(BlockifyDocumentType type, double value) {
    }
}
