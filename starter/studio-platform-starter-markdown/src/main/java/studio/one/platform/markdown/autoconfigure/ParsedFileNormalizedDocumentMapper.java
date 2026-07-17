package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.ExtractedImage;
import studio.one.platform.textract.domain.model.ExtractedTable;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;

public class ParsedFileNormalizedDocumentMapper {

    private static final Pattern LECTURE_HEADING = Pattern.compile("(?i)^\\s*(Lecture\\s*)?\\d{0,2}\\s*(다항식|개념|유형|CHECK|실력|풀이|빠른답|바른답).{0,40}$");
    private static final Pattern PROBLEM_NUMBER = Pattern.compile("^\\s*(\\d{2,4}|[①-⑳]|\\([0-9]+\\))\\b.*");
    private static final Pattern OCR_PROBLEM_IDENTIFIER = Pattern.compile("(?<!\\d)(0\\d{2})(?:[e6]|중+)?(?!\\d)");

    public NormalizedDocument map(ParsedFile parsed, String documentId, String filename,
            String sourceFormat, String fallbackMarkdown) {
        if (parsed == null) {
            return fallbackDocument(documentId, filename, sourceFormat, fallbackMarkdown, "NULL_PARSED_FILE");
        }
        List<NormalizedBlock> blocks = new ArrayList<>();
        int[] order = {0};
        for (ParsedBlock block : parsed.blocks()) {
            addBlock(blocks, block, order, "");
        }
        if (blocks.isEmpty()) {
            for (ParsedBlock page : parsed.pages()) {
                addBlock(blocks, page, order, "");
            }
        }
        for (ExtractedTable table : parsed.tables()) {
            blocks.add(tableBlock(table, order[0]++));
        }
        for (ExtractedImage image : parsed.images()) {
            NormalizedBlock imageBlock = imageBlock(image, order[0]++);
            blocks.add(imageBlock);
            String caption = image.caption();
            if (!caption.isBlank()) {
                blocks.add(NormalizedBlock.builder(NormalizedBlockType.IMAGE_CAPTION, caption)
                        .id(imageBlock.id() + ":caption")
                        .sourceRef(image.sourceRef())
                        .page(image.page())
                        .slide(image.slide())
                        .order(order[0]++)
                        .parentBlockId(imageBlock.id())
                        .metadata(Map.of("imageBlockId", imageBlock.id()))
                        .build());
            }
        }
        blocks = replacePageContentWithTextCorrection(blocks);
        blocks = replacePageContentWithMathReplacement(blocks);
        blocks = rebuildPageLayout(blocks);
        blocks = cleanMathCorrectionBlocks(blocks);
        blocks = replaceOverlappingMathBlocks(blocks);
        blocks = placeMathCorrectionsWithPageContent(blocks);
        blocks = classifyContentRoles(blocks);
        appendPageSearchContextBlocks(blocks, order, documentId);
        String plainText = firstText(parsed.markdown(), parsed.plainText(), fallbackMarkdown);
        if (blocks.isEmpty() && !plainText.isBlank()) {
            blocks.add(NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, plainText)
                    .id(documentId)
                    .order(0)
                    .metadata(Map.of("normalizationIssue", "FALLBACK_DOCUMENT_BLOCK"))
                    .build());
        }
        Map<String, Object> metadata = new LinkedHashMap<>(parsed.metadata());
        metadata.putIfAbsent("contentFormat", parsed.contentFormat());
        metadata.putIfAbsent("ocrApplied", parsed.ocrApplied());
        metadata.putIfAbsent("parseWarningCount", parsed.warnings().size());
        putPostProcessingMetadata(metadata, blocks);
        return NormalizedDocument.builder(documentId)
                .plainText(plainText)
                .sourceFormat(sourceFormat)
                .filename(filename)
                .blocks(blocks)
                .metadata(metadata)
                .build();
    }

    private NormalizedDocument fallbackDocument(String documentId, String filename, String sourceFormat,
            String text, String issue) {
        String content = text == null ? "" : text;
        List<NormalizedBlock> blocks = content.isBlank()
                ? List.of()
                : List.of(NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, content)
                        .id(documentId)
                        .order(0)
                        .metadata(Map.of("normalizationIssue", issue))
                        .build());
        return NormalizedDocument.builder(documentId)
                .plainText(content)
                .sourceFormat(sourceFormat)
                .filename(filename)
                .blocks(blocks)
                .metadata(Map.of("normalizationIssue", issue))
                .build();
    }

    private void addBlock(List<NormalizedBlock> blocks, ParsedBlock block, int[] order, String headingPath) {
        if (block == null) {
            return;
        }
        NormalizedBlock normalized = block(block, order[0]++, headingPath);
        if (normalized.hasText()) {
            addNormalizedBlock(blocks, normalized);
        }
        String childHeadingPath = normalized.type() == NormalizedBlockType.HEADING
                || normalized.type() == NormalizedBlockType.TITLE
                        ? normalized.text()
                        : headingPath;
        for (ParsedBlock child : block.children()) {
            addBlock(blocks, child, order, childHeadingPath);
        }
    }

    private NormalizedBlock block(ParsedBlock block, int order, String headingPath) {
        Map<String, Object> metadata = new LinkedHashMap<>(block.metadata());
        metadata.putIfAbsent("originalType", block.blockType().name());
        String text = text(block);
        if (text.isBlank() && ocrSource(block)
                && OcrTextNormalizer.isDiscardableNoiseLine(block.text())) {
            text = block.text().trim();
            metadata.put("searchContextOnly", true);
            metadata.put("discardedOcrNoise", true);
            metadata.put("discardReason", "OCR_NOISE");
        }
        NormalizedBlockType type = type(block.blockType(), text, metadata);
        if (block.blockType() == BlockType.OCR_TEXT && type != NormalizedBlockType.OCR_TEXT) {
            metadata.put("reclassifiedFrom", BlockType.OCR_TEXT.name());
            metadata.put("reclassificationRule", type.name());
        }
        return NormalizedBlock.builder(type, text)
                .id(block.id())
                .sourceRef(block.sourceRef())
                .page(block.page())
                .slide(block.slide())
                .order(block.order() == null ? order : block.order())
                .parentBlockId(block.parentBlockId())
                .headingPath(headingPath)
                .blockIds(List.of(block.id()))
                .confidence(block.confidence())
                .metadata(metadata)
                .build();
    }

    private void addNormalizedBlock(List<NormalizedBlock> blocks, NormalizedBlock next) {
        if (blocks.isEmpty() || !mergeableFragment(blocks.get(blocks.size() - 1), next)) {
            blocks.add(next);
            return;
        }
        NormalizedBlock previous = blocks.remove(blocks.size() - 1);
        blocks.add(mergeBlocks(previous, next, "SHORT_OCR_FRAGMENT"));
    }

    private NormalizedBlock mergeBlocks(NormalizedBlock previous, NormalizedBlock next, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>(previous.metadata());
        metadata.put("lineMergeApplied", true);
        metadata.put("lineMergeSourceBlockId", next.id());
        metadata.put("lineMergeReason", reason);
        List<String> blockIds = new ArrayList<>(previous.blockIds());
        blockIds.addAll(next.blockIds());
        String separator = mergeSeparator(previous.text(), next.text());
        return NormalizedBlock.builder(previous.type(), previous.text() + separator + next.text())
                .id(previous.id())
                .sourceRef(firstText(previous.sourceRef(), next.sourceRef()))
                .page(previous.page() == null ? next.page() : previous.page())
                .slide(previous.slide() == null ? next.slide() : previous.slide())
                .order(previous.order())
                .parentBlockId(previous.parentBlockId())
                .headingPath(previous.headingPath())
                .blockIds(blockIds)
                .confidence(previous.confidence())
                .metadata(metadata)
                .build();
    }

    private String mergeSeparator(String previous, String next) {
        if (previous == null || previous.isBlank() || next == null || next.isBlank()) {
            return "";
        }
        if (hasHangul(previous) && hasHangul(next)
                && previous.matches(".*[가-힣]$")
                && next.matches("^[가-힣].*")) {
            return "";
        }
        if (previous.endsWith(" ") || next.startsWith(" ")) {
            return "";
        }
        return " ";
    }

    private boolean mergeableFragment(NormalizedBlock previous, NormalizedBlock next) {
        if (previous == null || next == null || !samePage(previous, next)) {
            return false;
        }
        if (!paragraphLike(previous.type()) || !paragraphLike(next.type())) {
            return false;
        }
        String fragment = next.text();
        if (questionOrChoiceLabel(previous.text()) || questionOrChoiceLabel(fragment)) {
            return false;
        }
        if (fragment.matches(".*[.!?。]$")) {
            return false;
        }
        if (previous.text().endsWith(" ") || previous.text().matches(".*[.!?。]$")) {
            return false;
        }
        if (!hangulFragmentCandidate(previous.text(), fragment)) {
            return false;
        }
        return sameLineOrMissingBbox(previous, next);
    }

    private boolean hangulFragmentCandidate(String previous, String next) {
        if (previous == null || next == null) {
            return false;
        }
        String left = previous.trim();
        String right = next.trim();
        if (left.isBlank() || right.isBlank()) {
            return false;
        }
        if (!hasHangul(left) || !hasHangul(right)) {
            return false;
        }
        if (right.length() <= 5) {
            return true;
        }
        return left.length() <= 5
                && left.matches("[가-힣]+")
                && right.matches("[가-힣][가-힣\\s.,:;()0-9A-Za-z+-]{2,80}");
    }

    private boolean hasHangul(String text) {
        return text.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3);
    }

    private boolean sameLineOrMissingBbox(NormalizedBlock previous, NormalizedBlock next) {
        Double previousY = bboxY(previous.metadata().get("bbox"));
        Double nextY = bboxY(next.metadata().get("bbox"));
        Double previousHeight = bboxHeight(previous.metadata().get("bbox"));
        if (previousY == null || nextY == null) {
            return true;
        }
        double tolerance = Math.max(8.0d, previousHeight == null ? 12.0d : previousHeight * 1.2d);
        return Math.abs(previousY - nextY) <= tolerance;
    }

    private Double bboxY(Object bbox) {
        if (bbox instanceof Map<?, ?> map) {
            return number(map.get("y"));
        }
        if (bbox instanceof List<?> list && list.size() >= 2) {
            return number(list.get(1));
        }
        return null;
    }

    private Double bboxHeight(Object bbox) {
        if (bbox instanceof Map<?, ?> map) {
            return number(map.get("height"));
        }
        if (bbox instanceof List<?> list && list.size() >= 4) {
            Double y1 = number(list.get(1));
            Double y2 = number(list.get(3));
            if (y1 != null && y2 != null) {
                return Math.abs(y2 - y1);
            }
        }
        return null;
    }

    private Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean paragraphLike(NormalizedBlockType type) {
        return type == NormalizedBlockType.PARAGRAPH
                || type == NormalizedBlockType.OCR_TEXT
                || type == NormalizedBlockType.HEADING;
    }

    private List<NormalizedBlock> rebuildPageLayout(List<NormalizedBlock> blocks) {
        if (blocks.isEmpty()) {
            return blocks;
        }
        List<NormalizedBlock> visible = new ArrayList<>();
        for (NormalizedBlock block : blocks) {
            if (discardableOcrNoise(block)) {
                visible.add(markHidden(block, "OCR_NOISE"));
            } else {
                visible.add(block);
            }
        }
        visible = mergePageWindowFragments(visible);
        visible = mergeBboxLineFragments(visible);
        visible = clusterMathFragments(visible);
        visible = composePageParagraphs(visible);
        return visible;
    }

    private List<NormalizedBlock> mergePageWindowFragments(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> result = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            NormalizedBlock current = blocks.get(i);
            if (hidden(current)) {
                result.add(current);
                continue;
            }
            if (i + 1 < blocks.size()) {
                NormalizedBlock next = blocks.get(i + 1);
                if (!hidden(next) && mergeableFragment(current, next)) {
                    result.add(mergeBlocks(current, next, "PAGE_WINDOW_FRAGMENT"));
                    i++;
                    continue;
                }
            }
            if (!result.isEmpty()) {
                NormalizedBlock previous = result.get(result.size() - 1);
                if (!hidden(previous) && mergeableFragment(previous, current)) {
                    result.set(result.size() - 1, mergeBlocks(previous, current, "PAGE_WINDOW_FRAGMENT"));
                    continue;
                }
            }
            result.add(current);
        }
        return result;
    }

    private List<NormalizedBlock> mergeBboxLineFragments(List<NormalizedBlock> blocks) {
        Map<Integer, List<Integer>> byPage = new LinkedHashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            NormalizedBlock block = blocks.get(i);
            Integer page = block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page();
            if (page == null || hidden(block) || bbox(block) == null || !paragraphLike(block.type())) {
                continue;
            }
            byPage.computeIfAbsent(page, ignored -> new ArrayList<>()).add(i);
        }
        Map<Integer, NormalizedBlock> replacements = new LinkedHashMap<>();
        java.util.Set<Integer> removed = new java.util.HashSet<>();
        for (List<Integer> indexes : byPage.values()) {
            indexes.sort(Comparator
                    .comparingDouble((Integer index) -> bbox(blocks.get(index))[1])
                    .thenComparingDouble(index -> bbox(blocks.get(index))[0]));
            List<List<Integer>> lines = lineGroups(indexes, blocks);
            for (List<Integer> line : lines) {
                if (line.size() < 2) {
                    continue;
                }
                NormalizedBlock merged = null;
                for (Integer index : line) {
                    NormalizedBlock block = blocks.get(index);
                    if (merged == null) {
                        merged = block;
                    } else if (layoutMergeable(merged, block)) {
                        merged = mergeBlocks(merged, block, "BBOX_LINE_FRAGMENT");
                        removed.add(index);
                    }
                }
                if (merged != null && !line.isEmpty()) {
                    replacements.put(line.get(0), merged);
                }
            }
        }
        if (replacements.isEmpty() && removed.isEmpty()) {
            return blocks;
        }
        List<NormalizedBlock> result = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            if (removed.contains(i)) {
                continue;
            }
            result.add(replacements.getOrDefault(i, blocks.get(i)));
        }
        return result;
    }

    private List<List<Integer>> lineGroups(List<Integer> indexes, List<NormalizedBlock> blocks) {
        List<List<Integer>> groups = new ArrayList<>();
        for (Integer index : indexes) {
            double[] bbox = bbox(blocks.get(index));
            if (bbox == null) {
                continue;
            }
            if (groups.isEmpty()) {
                groups.add(new ArrayList<>(List.of(index)));
                continue;
            }
            List<Integer> current = groups.get(groups.size() - 1);
            double[] previous = bbox(blocks.get(current.get(current.size() - 1)));
            double previousHeight = Math.max(1.0d, previous[3] - previous[1]);
            if (Math.abs(bbox[1] - previous[1]) <= Math.max(6.0d, previousHeight * 0.65d)) {
                current.add(index);
            } else {
                groups.add(new ArrayList<>(List.of(index)));
            }
        }
        return groups;
    }

    private boolean layoutMergeable(NormalizedBlock previous, NormalizedBlock next) {
        if (previous == null || next == null || !samePage(previous, next)) {
            return false;
        }
        String right = next.text();
        String left = previous.text();
        if (right == null || left == null || right.isBlank() || left.isBlank()) {
            return false;
        }
        if (mathCorrectionBlock(previous) || mathCorrectionBlock(next)) {
            return false;
        }
        if (hasHangul(left) && hasHangul(right)) {
            return left.length() <= 80 || right.length() <= 8;
        }
        return shortFragment(right) && (hasHangul(left) || looksLikeBrokenMath(left));
    }

    private List<NormalizedBlock> clusterMathFragments(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> result = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            NormalizedBlock current = blocks.get(i);
            if (hidden(current) || !shortMathFragment(current)) {
                result.add(current);
                continue;
            }
            NormalizedBlock merged = current;
            int j = i + 1;
            while (j < blocks.size()) {
                NormalizedBlock next = blocks.get(j);
                if (hidden(next) || !samePage(merged, next) || !shortMathFragment(next)) {
                    break;
                }
                merged = mergeBlocks(merged, next, "MATH_CLUSTER_FRAGMENT");
                j++;
            }
            result.add(merged);
            i = j - 1;
        }
        return result;
    }

    private List<NormalizedBlock> composePageParagraphs(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> result = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            NormalizedBlock current = blocks.get(i);
            if (hidden(current)) {
                result.add(current);
                continue;
            }
            NormalizedBlock previous = lastVisible(result);
            NormalizedBlock next = nextVisible(blocks, i + 1);
            if (discardableShortArtifact(current, previous, next)) {
                result.add(markHidden(current, "SHORT_OCR_ARTIFACT"));
                continue;
            }
            if (previous != null && paragraphComposerMergeable(previous, current, next)) {
                result.set(result.size() - 1, mergeBlocks(previous, current, "PAGE_PARAGRAPH_COMPOSER"));
                continue;
            }
            if (next != null && paragraphComposerMergeable(current, next, null)) {
                result.add(mergeBlocks(current, next, "PAGE_PARAGRAPH_COMPOSER"));
                i = indexOf(blocks, next, i + 1);
                continue;
            }
            result.add(current);
        }
        return result;
    }

    private NormalizedBlock lastVisible(List<NormalizedBlock> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            NormalizedBlock block = blocks.get(i);
            if (!hidden(block)) {
                return block;
            }
        }
        return null;
    }

    private NormalizedBlock nextVisible(List<NormalizedBlock> blocks, int start) {
        for (int i = start; i < blocks.size(); i++) {
            NormalizedBlock block = blocks.get(i);
            if (!hidden(block)) {
                return block;
            }
        }
        return null;
    }

    private int indexOf(List<NormalizedBlock> blocks, NormalizedBlock target, int start) {
        for (int i = start; i < blocks.size(); i++) {
            if (blocks.get(i) == target) {
                return i;
            }
        }
        return start;
    }

    private boolean paragraphComposerMergeable(NormalizedBlock previous, NormalizedBlock current,
            NormalizedBlock next) {
        if (previous == null || current == null || !samePage(previous, current)) {
            return false;
        }
        if (!paragraphComposerCandidate(previous) || !paragraphComposerCandidate(current)) {
            return false;
        }
        if (mathCorrectionBlock(previous) || mathCorrectionBlock(current)) {
            return false;
        }
        if (previous.type() == NormalizedBlockType.LIST_ITEM || current.type() == NormalizedBlockType.LIST_ITEM) {
            return false;
        }
        String left = previous.text() == null ? "" : previous.text().trim();
        String right = current.text() == null ? "" : current.text().trim();
        if (left.isBlank() || right.isBlank()) {
            return false;
        }
        if (questionOrChoiceLabel(left) || questionOrChoiceLabel(right)) {
            return false;
        }
        if (shortMathFragment(previous) || shortMathFragment(current)) {
            return shortMathFragment(previous) && shortMathFragment(current);
        }
        if (right.length() <= 8 && hasHangul(right) && hasHangul(left)) {
            return previous.type() != NormalizedBlockType.HEADING
                    || right.length() <= 5
                    || (next != null && samePage(current, next));
        }
        if (left.length() <= 5 && hasHangul(left) && hasHangul(right)) {
            return true;
        }
        if (koreanContinuationMergeable(previous, current)) {
            return true;
        }
        if (right.matches("^\\([0-9]+\\)\\s*[가-힣]$")) {
            return true;
        }
        return false;
    }

    private boolean koreanContinuationMergeable(NormalizedBlock previous, NormalizedBlock current) {
        String left = previous.text() == null ? "" : previous.text().trim();
        String right = current.text() == null ? "" : current.text().trim();
        if (!hasHangul(left) || !hasHangul(right)
                || left.matches(".*[.!?。:]$") || right.matches("^(?:문제|예제|유형)\\s*\\d+.*")) {
            return false;
        }
        double[] leftBox = bbox(previous);
        double[] rightBox = bbox(current);
        if (leftBox == null || rightBox == null) {
            return false;
        }
        double leftHeight = Math.max(1.0d, leftBox[3] - leftBox[1]);
        double verticalGap = rightBox[1] - leftBox[3];
        return Math.abs(leftBox[0] - rightBox[0]) <= Math.max(12.0d, leftHeight)
                && verticalGap >= -leftHeight * 0.5d
                && verticalGap <= leftHeight * 1.5d;
    }

    private boolean questionOrChoiceLabel(String text) {
        String value = text == null ? "" : text.trim();
        return value.matches("^(?:문제|예제|유형)\\s*\\d{1,4}(?:\\s*[:.)-])?$")
                || value.matches("^\\([1-9][0-9]?\\)\\s+.+")
                || value.matches("^\\d{3,4}$");
    }

    private boolean paragraphComposerCandidate(NormalizedBlock block) {
        if (block == null || hidden(block)) {
            return false;
        }
        if (block.type() == NormalizedBlockType.TABLE
                || block.type() == NormalizedBlockType.IMAGE
                || block.type() == NormalizedBlockType.IMAGE_CAPTION
                || block.type() == NormalizedBlockType.PAGE
                || block.type() == NormalizedBlockType.METADATA) {
            return false;
        }
        return paragraphLike(block.type()) || block.type() == NormalizedBlockType.LIST_ITEM;
    }

    private boolean discardableShortArtifact(NormalizedBlock current, NormalizedBlock previous,
            NormalizedBlock next) {
        if (current == null || !ocrOrigin(current) || mathCorrectionBlock(current)) {
            return false;
        }
        String text = current.text() == null ? "" : current.text().trim();
        if (text.isBlank()) {
            return false;
        }
        if (text.matches("^[=\\-_/|~.,:;!<>]+$")) {
            return !nearMath(previous, next);
        }
        if (text.matches("(?i)^/[\\sA-Za-z~_-]{1,8}$")) {
            return true;
        }
        if (text.matches("^\\d$")) {
            return !nearProblemContext(previous, next);
        }
        return OcrTextNormalizer.isDiscardableNoiseLine(text)
                && !hasHangul(text)
                && !nearMath(previous, next);
    }

    private boolean nearMath(NormalizedBlock previous, NormalizedBlock next) {
        return (previous != null && looksLikeMathText(previous.text()))
                || (next != null && looksLikeMathText(next.text()));
    }

    private boolean nearProblemContext(NormalizedBlock previous, NormalizedBlock next) {
        return (previous != null && previous.type() == NormalizedBlockType.LIST_ITEM)
                || (next != null && next.type() == NormalizedBlockType.LIST_ITEM)
                || (next != null && next.text() != null && next.text().trim().matches("^\\d{2,4}\\b.*"));
    }

    private boolean shortMathFragment(NormalizedBlock block) {
        if (block == null || hidden(block) || mathCorrectionBlock(block)) {
            return false;
        }
        String text = block.text() == null ? "" : block.text().trim();
        if (text.isBlank() || text.length() > 32) {
            return false;
        }
        return text.matches("^\\$[^$]{1,28}\\$$")
                || text.matches("^[=+\\-*/^]$")
                || (looksLikeMathText(text) && text.length() <= 16 && !hasHangul(text));
    }

    private boolean discardableOcrNoise(NormalizedBlock block) {
        if (block == null || !ocrOrigin(block)) {
            return false;
        }
        String text = block.text() == null ? "" : block.text().trim();
        if (text.isBlank() || mathCorrectionBlock(block) || meaningfulShortLine(text)) {
            return false;
        }
        return OcrTextNormalizer.isDiscardableNoiseLine(text)
                || text.matches("^[=\\-_/|~.,:;!<>]+$")
                || text.matches("(?i)^[A-Za-z0-9]{1,5}[>!~]$")
                || text.matches("(?i)^/[\\sA-Za-z~_-]{1,8}$");
    }

    private boolean meaningfulShortLine(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String value = text.trim();
        return value.matches("^\\$[^$]+\\$$")
                || value.matches("^\\d{2,4}$")
                || value.matches("^[①-⑳]$")
                || value.matches("^\\([0-9]+\\)$");
    }

    private boolean shortFragment(String text) {
        return text != null && !text.isBlank() && text.trim().length() <= 5 && !meaningfulShortLine(text);
    }

    private boolean hidden(NormalizedBlock block) {
        return block != null && Boolean.TRUE.equals(block.metadata().get("searchContextOnly"));
    }

    private boolean ocrOrigin(NormalizedBlock block) {
        if (block == null) {
            return false;
        }
        Object originalType = block.metadata().get("originalType");
        return block.type() == NormalizedBlockType.OCR_TEXT
                || "OCR_TEXT".equals(originalType)
                || Boolean.TRUE.equals(block.metadata().get("ocrApplied"))
                || block.metadata().containsKey("reclassifiedFrom");
    }

    private NormalizedBlock markHidden(NormalizedBlock block, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>(block.metadata());
        metadata.put("searchContextOnly", true);
        metadata.put("discardedOcrNoise", true);
        metadata.put("discardReason", reason);
        return NormalizedBlock.builder(block.type(), block.text())
                .id(block.id())
                .sourceRef(block.sourceRef())
                .page(block.page())
                .slide(block.slide())
                .order(block.order())
                .parentBlockId(block.parentBlockId())
                .headingPath(block.headingPath())
                .blockIds(block.blockIds())
                .confidence(block.confidence())
                .metadata(metadata)
                .build();
    }

    private List<NormalizedBlock> replaceOverlappingMathBlocks(List<NormalizedBlock> blocks) {
        if (blocks.isEmpty()) {
            return blocks;
        }
        List<NormalizedBlock> replacements = blocks.stream()
                .filter(this::mathCorrectionBlock)
                .filter(block -> block.page() != null)
                .filter(this::validMathCorrection)
                .toList();
        if (replacements.isEmpty()) {
            return blocks;
        }
        List<NormalizedBlock> result = new ArrayList<>();
        for (NormalizedBlock block : blocks) {
            if (!mathCorrectionBlock(block) && replaceableMathOcrBlock(block, replacements)) {
                result.add(markSuppressed(block));
                continue;
            }
            result.add(block);
        }
        return result;
    }

    private List<NormalizedBlock> replacePageContentWithMathReplacement(List<NormalizedBlock> blocks) {
        java.util.Set<Integer> replacementPages = blocks.stream()
                .filter(this::mathPageContentReplacement)
                .map(block -> block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (replacementPages.isEmpty()) {
            return blocks;
        }
        List<NormalizedBlock> result = new ArrayList<>();
        for (NormalizedBlock block : blocks) {
            Integer page = block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page();
            if (page != null && replacementPages.contains(page)
                    && !mathPageContentReplacement(block)
                    && !mathCorrectionBlock(block)
                    && block.type() != NormalizedBlockType.IMAGE
                    && block.type() != NormalizedBlockType.IMAGE_CAPTION) {
                result.add(markHidden(block, "REPLACED_BY_MATH_PAGE_OCR"));
            } else {
                result.add(block);
            }
        }
        return result;
    }

    private List<NormalizedBlock> replacePageContentWithTextCorrection(List<NormalizedBlock> blocks) {
        java.util.Set<Integer> replacementPages = eligibleTextReplacementPages(blocks);
        if (replacementPages.isEmpty()) {
            return blocks;
        }
        List<NormalizedBlock> result = new ArrayList<>(blocks.size());
        java.util.Set<String> emittedProblemMarkers = new java.util.LinkedHashSet<>();
        for (NormalizedBlock block : blocks) {
            Integer page = block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page();
            boolean replacementPage = page != null
                    && replacementPages.contains(page)
                    && !textCorrectionBlock(block);
            boolean preserve = !replacementPage
                    || block.type() == NormalizedBlockType.IMAGE
                    || block.type() == NormalizedBlockType.IMAGE_CAPTION
                    || mathPageContentReplacement(block)
                    || (mathCorrectionBlock(block) && validMathCorrection(block))
                    || (!mathCorrectionBlock(block) && validBaselineMathBlock(block));
            if (preserve) {
                result.add(block);
                continue;
            }
            for (String problemNumber : problemIdentifiers(block.text())) {
                String markerKey = page + ":" + problemNumber;
                if (emittedProblemMarkers.add(markerKey)) {
                    result.add(problemMarker(block, page, problemNumber));
                }
            }
            result.add(markHidden(block, "REPLACED_BY_KOREAN_TEXT_OCR"));
        }
        return result;
    }

    private List<String> problemIdentifiers(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return OCR_PROBLEM_IDENTIFIER.matcher(text).results()
                .map(result -> result.group(1))
                .distinct()
                .toList();
    }

    private NormalizedBlock problemMarker(NormalizedBlock source, Integer page, String problemNumber) {
        Map<String, Object> metadata = new LinkedHashMap<>(source.metadata());
        metadata.remove("searchContextOnly");
        metadata.remove("discardedOcrNoise");
        metadata.remove("discardReason");
        metadata.put("derivedProblemMarker", true);
        metadata.put("problemNumber", problemNumber);
        String sourceRef = source.sourceRef() + "/problem[" + problemNumber + "]";
        return NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "문제 " + problemNumber)
                .id(source.id() + "-problem-" + problemNumber)
                .sourceRef(sourceRef)
                .page(page)
                .slide(source.slide())
                .order(source.order())
                .parentBlockId(source.parentBlockId())
                .headingPath(source.headingPath())
                .confidence(source.confidence())
                .metadata(metadata)
                .build();
    }

    /**
     * Hybrid math engines emit page-scoped supplement blocks after native extraction.
     * Rebuild a single document order so supplements stay with their source page instead
     * of being moved to the beginning by duplicate engine-local order values.
     */
    private List<NormalizedBlock> placeMathCorrectionsWithPageContent(List<NormalizedBlock> blocks) {
        if (blocks.isEmpty()) {
            return blocks;
        }
        Map<Integer, List<NormalizedBlock>> pageContent = new LinkedHashMap<>();
        Map<Integer, List<NormalizedBlock>> pageCorrections = new LinkedHashMap<>();
        for (NormalizedBlock block : blocks) {
            Integer page = block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page();
            Map<Integer, List<NormalizedBlock>> target = mathCorrectionBlock(block) && page != null
                    ? pageCorrections
                    : pageContent;
            target.computeIfAbsent(page, ignored -> new ArrayList<>()).add(block);
            pageContent.computeIfAbsent(page, ignored -> new ArrayList<>());
        }

        List<NormalizedBlock> ordered = new ArrayList<>();
        for (Map.Entry<Integer, List<NormalizedBlock>> entry : pageContent.entrySet()) {
            ordered.addAll(entry.getValue());
            ordered.addAll(pageCorrections.getOrDefault(entry.getKey(), List.of()));
        }
        for (Map.Entry<Integer, List<NormalizedBlock>> entry : pageCorrections.entrySet()) {
            if (!pageContent.containsKey(entry.getKey())) {
                ordered.addAll(entry.getValue());
            }
        }

        List<NormalizedBlock> resequenced = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            resequenced.add(withOrder(ordered.get(index), index));
        }
        return resequenced;
    }

    private List<NormalizedBlock> classifyContentRoles(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> classified = new ArrayList<>(blocks.size());
        for (NormalizedBlock block : blocks) {
            Map<String, Object> metadata = new LinkedHashMap<>(block.metadata());
            metadata.putIfAbsent("contentRole", contentRole(block));
            classified.add(NormalizedBlock.builder(block.type(), block.text())
                    .id(block.id())
                    .sourceRef(block.sourceRef())
                    .page(block.page())
                    .slide(block.slide())
                    .order(block.order())
                    .parentBlockId(block.parentBlockId())
                    .headingPath(block.headingPath())
                    .blockIds(block.blockIds())
                    .confidence(block.confidence())
                    .metadata(metadata)
                    .build());
        }
        return classified;
    }

    private String contentRole(NormalizedBlock block) {
        if (block.type() == NormalizedBlockType.TABLE) {
            return "TABLE";
        }
        if (block.type() == NormalizedBlockType.TITLE || block.type() == NormalizedBlockType.HEADING) {
            return "SECTION";
        }
        String text = block.text() == null ? "" : block.text().trim();
        if (text.matches("^(?:문제|예제|유형)\\s*\\d{1,4}(?:\\s*[:.)-])?$")
                || text.matches("^\\d{3,4}$")) {
            return "QUESTION";
        }
        if (text.matches("^\\([1-9][0-9]?\\)\\s+.+")) {
            return "CHOICE";
        }
        if (mathCorrectionBlock(block) || looksLikeMathText(text)) {
            return "MATH";
        }
        return "BODY";
    }

    private NormalizedBlock withOrder(NormalizedBlock block, int order) {
        return NormalizedBlock.builder(block.type(), block.text())
                .id(block.id())
                .sourceRef(block.sourceRef())
                .page(block.page())
                .slide(block.slide())
                .order(order)
                .parentBlockId(block.parentBlockId())
                .headingPath(block.headingPath())
                .blockIds(block.blockIds())
                .confidence(block.confidence())
                .metadata(block.metadata())
                .build();
    }

    private List<NormalizedBlock> cleanMathCorrectionBlocks(List<NormalizedBlock> blocks) {
        if (blocks.isEmpty()) {
            return blocks;
        }
        Map<Integer, List<String>> fingerprintsByPage = new LinkedHashMap<>();
        List<NormalizedBlock> result = new ArrayList<>();
        for (NormalizedBlock block : blocks) {
            if (!mathCorrectionBlock(block) || hidden(block)) {
                result.add(block);
                continue;
            }
            String text = block.text() == null ? "" : block.text().trim();
            if (formulaCorrectionBlock(block) && !validMathCorrection(block)) {
                result.add(markHidden(block, "INVALID_MATH_CORRECTION"));
                continue;
            }
            if (discardableMathCorrection(text)) {
                result.add(markHidden(block, "MATH_CORRECTION_NOISE"));
                continue;
            }
            Integer page = block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page();
            String fingerprint = mathFingerprint(text);
            if (page != null && !fingerprint.isBlank()) {
                List<String> seen = fingerprintsByPage.computeIfAbsent(page, ignored -> new ArrayList<>());
                if (seen.stream().anyMatch(value -> value.equals(fingerprint)
                        || mathFingerprintSimilarity(value, fingerprint) >= 0.92d)) {
                    result.add(markHidden(block, "DUPLICATE_MATH_CORRECTION"));
                    continue;
                }
                seen.add(fingerprint);
            }
            result.add(block);
        }
        return result;
    }

    private boolean discardableMathCorrection(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String value = text.trim();
        if (OcrTextNormalizer.isDiscardableNoiseLine(value)) {
            return true;
        }
        if (value.matches("^\\$\\s*[0-9A-Za-z]\\s*\\$$")
                || value.matches("^\\$\\s*[0-9A-Za-z]\\s*['’]?\\s*\\$$")) {
            return true;
        }
        String unwrapped = unwrapMath(value);
        if (unwrapped.length() <= 2 && !unwrapped.matches(".*[=+\\-*/^].*")) {
            return true;
        }
        return unwrapped.matches("(?i)^(OO+|SS|SAS|NOS|Liat!?|Leal|Lexesoll|FAIO|CAD|HHH|go)$");
    }

    private String mathFingerprint(String text) {
        String value = unwrapMath(text);
        if (value.isBlank()) {
            return "";
        }
        return value
                .replace('−', '-')
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("\\\\left|\\\\right", "")
                .replaceAll("\\\\mathrm\\{([^}]*)}", "$1")
                .replaceAll("\\\\text\\{([^}]*)}", "$1")
                .replaceAll("[\\s{}]", "")
                .replaceAll("\\^\\{([^}]*)}", "^$1")
                .replaceAll("_\\{([^}]*)}", "_$1")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private String unwrapMath(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("$$") && value.endsWith("$$") && value.length() > 4) {
            value = value.substring(2, value.length() - 2).trim();
        }
        if (value.startsWith("$") && value.endsWith("$") && value.length() > 2) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private double mathFingerprintSimilarity(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return 0.0d;
        }
        int max = Math.max(left.length(), right.length());
        if (max == 0) {
            return 1.0d;
        }
        int distance = levenshtein(left, right);
        return 1.0d - ((double) distance / max);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private boolean replaceableMathOcrBlock(NormalizedBlock block, List<NormalizedBlock> replacements) {
        if (block == null || block.page() == null || !paragraphLike(block.type()) || !looksLikeBrokenMath(block.text())) {
            return false;
        }
        for (NormalizedBlock replacement : replacements) {
            if (!java.util.Objects.equals(block.page(), replacement.page())) {
                continue;
            }
            if (mergePriority(replacement) < mergePriority(block)) {
                continue;
            }
            double[] blockBox = bbox(block);
            double[] replacementBox = bbox(replacement);
            if (blockBox != null && replacementBox != null && overlaps(blockBox, replacementBox)) {
                return true;
            }
            if (mathTokenSimilarity(block.text(), replacement.text()) >= 0.35d) {
                return true;
            }
        }
        return false;
    }

    private java.util.Set<Integer> eligibleTextReplacementPages(List<NormalizedBlock> blocks) {
        Map<Integer, StringBuilder> baseline = new LinkedHashMap<>();
        Map<Integer, StringBuilder> corrections = new LinkedHashMap<>();
        for (NormalizedBlock block : blocks) {
            Integer page = block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page();
            if (page == null || block.text() == null || block.text().isBlank()) {
                continue;
            }
            boolean correction = textCorrectionBlock(block);
            if ((!correction && looksLikeMathText(block.text())) || (correction && !hasHangul(block.text()))) {
                continue;
            }
            Map<Integer, StringBuilder> target = correction ? corrections : baseline;
            target.computeIfAbsent(page, ignored -> new StringBuilder()).append(block.text()).append('\n');
        }
        java.util.Set<Integer> eligible = new java.util.LinkedHashSet<>();
        for (Map.Entry<Integer, StringBuilder> entry : corrections.entrySet()) {
            String corrected = entry.getValue().toString();
            String original = baseline.getOrDefault(entry.getKey(), new StringBuilder()).toString();
            boolean catastrophic = blocks.stream()
                    .filter(this::textCorrectionBlock)
                    .filter(block -> java.util.Objects.equals(entry.getKey(), block.page() == null
                            ? pageFromSourceRef(block.sourceRef()) : block.page()))
                    .anyMatch(block -> Boolean.TRUE.equals(block.metadata().get("catastrophicKoreanBaseline")));
            int minimumLength = Math.max(40, (int) Math.ceil(original.length() * (catastrophic ? 0.15d : 0.60d)));
            boolean ordinaryImprovement = jamoRatio(corrected) < jamoRatio(original);
            boolean catastrophicImprovement = catastrophic
                    && hangulLetterRatio(corrected) >= 0.20d
                    && asciiLatinLetterRatio(corrected) <= 0.35d;
            if (corrected.length() >= minimumLength && (ordinaryImprovement || catastrophicImprovement)) {
                eligible.add(entry.getKey());
            }
        }
        return eligible;
    }

    private boolean validMathCorrection(NormalizedBlock block) {
        String text = block == null || block.text() == null ? "" : block.text().trim();
        if (text.isBlank() || text.length() > 2048 || text.indexOf('\u3161') >= 0
                || text.contains("@$") || latinGarbling(text)) {
            return false;
        }
        long dollars = text.chars().filter(ch -> ch == '$').count();
        return looksLikeMathText(text) && dollars % 2 == 0;
    }

    private boolean formulaCorrectionBlock(NormalizedBlock block) {
        if (block == null) {
            return false;
        }
        Map<String, Object> metadata = block.metadata();
        return Boolean.TRUE.equals(metadata.get("mathVisionCorrectionOnly"))
                || Boolean.TRUE.equals(metadata.get("mathSupplementOnly"));
    }

    private boolean validBaselineMathBlock(NormalizedBlock block) {
        if (block == null || block.type() == NormalizedBlockType.TABLE || hidden(block)) {
            return false;
        }
        String text = block.text() == null ? "" : block.text().trim();
        if (text.isBlank() || text.length() > 512 || text.indexOf('\u3161') >= 0
                || text.contains("@$") || text.contains("\"$") || text.contains("$\"")
                || OcrTextNormalizer.isDiscardableNoiseLine(text)) {
            return false;
        }
        long dollars = text.chars().filter(ch -> ch == '$').count();
        if (dollars > 0) {
            return dollars % 2 == 0 && looksLikeMathText(text) && !latinGarbling(text);
        }
        String compact = text.replaceAll("\\s+", "");
        boolean conciseExpression = compact.length() >= 3
                && compact.length() <= 120
                && compact.matches(".*[=+\\-*/^].*")
                && compact.matches(".*[0-9A-Za-z].*")
                && !compact.matches(".*[가-힣].*")
                && !Pattern.compile("[A-Za-z]{4,}").matcher(compact).find();
        return conciseExpression && !latinGarbling(text);
    }

    private boolean latinGarbling(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String withoutLatexCommands = text.replaceAll("\\\\[A-Za-z]+", "");
        long longLatinTokens = Pattern.compile("[A-Za-z]{4,}").matcher(withoutLatexCommands).results().count();
        boolean knownCorruption = text.matches("(?is).*(Cref|CHSt|SAlO|CEAY|HUH|GES|Lexesoll|Liat).*");
        return knownCorruption || (!text.contains("\\") && longLatinTokens >= 2);
    }

    private int mergePriority(NormalizedBlock block) {
        Object value = block == null ? null : block.metadata().get("mergePriority");
        return value instanceof Number number ? number.intValue() : 100;
    }

    private double jamoRatio(String text) {
        if (text == null || text.isBlank()) {
            return 0.0d;
        }
        long jamo = text.chars().filter(ch -> (ch >= 0x3131 && ch <= 0x318E) || ch == 0xFFFD).count();
        return (double) jamo / text.length();
    }

    private double hangulLetterRatio(String text) {
        return letterRatio(text, true);
    }

    private double asciiLatinLetterRatio(String text) {
        return letterRatio(text, false);
    }

    private double letterRatio(String text, boolean hangul) {
        if (text == null || text.isBlank()) {
            return 0.0d;
        }
        long letters = text.codePoints().filter(Character::isLetter).count();
        if (letters == 0) {
            return 0.0d;
        }
        long selected = text.codePoints().filter(ch -> hangul
                ? ch >= 0xAC00 && ch <= 0xD7A3
                : (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')).count();
        return (double) selected / letters;
    }

    private NormalizedBlock markSuppressed(NormalizedBlock block) {
        Map<String, Object> metadata = new LinkedHashMap<>(block.metadata());
        metadata.put("searchContextOnly", true);
        metadata.put("suppressedByMathCorrection", true);
        metadata.put("suppressionReason", "OVERLAPPING_MATH_CORRECTION");
        return NormalizedBlock.builder(block.type(), block.text())
                .id(block.id())
                .sourceRef(block.sourceRef())
                .page(block.page())
                .slide(block.slide())
                .order(block.order())
                .parentBlockId(block.parentBlockId())
                .headingPath(block.headingPath())
                .blockIds(block.blockIds())
                .confidence(block.confidence())
                .metadata(metadata)
                .build();
    }

    private boolean mathCorrectionBlock(NormalizedBlock block) {
        if (block == null) {
            return false;
        }
        Map<String, Object> metadata = block.metadata();
        return Boolean.TRUE.equals(metadata.get("mathVisionCorrectionOnly"))
                || Boolean.TRUE.equals(metadata.get("mathSupplementOnly"))
                || Boolean.TRUE.equals(metadata.get("mathPageContentReplacement"))
                || "VISION_LLM".equals(metadata.get("mathSupplementSource"))
                || "HYBRID_MATH_OCR".equals(metadata.get("mathSupplementSource"));
    }

    private boolean textCorrectionBlock(NormalizedBlock block) {
        return block != null && Boolean.TRUE.equals(block.metadata().get("textCorrectionOnly"));
    }

    private boolean textPageContentReplacement(NormalizedBlock block) {
        return block != null && Boolean.TRUE.equals(block.metadata().get("textPageContentReplacement"));
    }

    private boolean mathPageContentReplacement(NormalizedBlock block) {
        return block != null && Boolean.TRUE.equals(block.metadata().get("mathPageContentReplacement"));
    }

    private boolean looksLikeBrokenMath(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String value = text.trim();
        long operators = value.chars().filter(ch -> "=+-*/^ㅡ".indexOf(ch) >= 0).count();
        boolean hasVariableOrDigit = value.matches(".*[0-9A-Za-z].*");
        return hasVariableOrDigit
                && (operators >= 2
                        || value.contains("ㅡ")
                        || value.contains("\"$")
                        || value.contains("$\"")
                        || value.contains("@$"));
    }

    private boolean looksLikeMathText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String value = text.trim();
        return value.contains("$")
                || value.contains("\\frac")
                || value.contains("\\sqrt")
                || value.matches(".*[0-9A-Za-z가-힣][=+\\-*/^][0-9A-Za-z가-힣({\\[].*")
                || value.matches(".*(^|[^A-Za-z가-힣])[xyab]\\s*(\\^\\s*\\d+|[=+\\-*/]).*")
                || value.matches(".*\\d\\s*[xyab](\\s*\\^\\s*\\d+)?.*");
    }

    private double mathTokenSimilarity(String left, String right) {
        java.util.Set<String> leftTokens = mathTokens(left);
        java.util.Set<String> rightTokens = mathTokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0d;
        }
        java.util.Set<String> intersection = new java.util.HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        java.util.Set<String> union = new java.util.HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0.0d : (double) intersection.size() / union.size();
    }

    private java.util.Set<String> mathTokens(String text) {
        if (text == null || text.isBlank()) {
            return java.util.Set.of();
        }
        java.util.Set<String> tokens = new java.util.HashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[A-Za-z]+|\\d+|[=+\\-*/^]").matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private boolean overlaps(double[] first, double[] second) {
        if (first == null || second == null) {
            return false;
        }
        double xOverlap = Math.max(0.0d, Math.min(first[2], second[2]) - Math.max(first[0], second[0]));
        double yOverlap = Math.max(0.0d, Math.min(first[3], second[3]) - Math.max(first[1], second[1]));
        double area = Math.max(1.0d, (first[2] - first[0]) * (first[3] - first[1]));
        return (xOverlap * yOverlap) / area >= 0.35d;
    }

    private double[] bbox(NormalizedBlock block) {
        if (block == null) {
            return null;
        }
        Object bbox = block.metadata().get("bbox");
        if (bbox instanceof Map<?, ?> map) {
            Double x = number(map.get("x"));
            Double y = number(map.get("y"));
            Double width = number(map.get("width"));
            Double height = number(map.get("height"));
            if (x != null && y != null && width != null && height != null) {
                return new double[] {x, y, x + width, y + height};
            }
        }
        if (bbox instanceof List<?> list && list.size() >= 4) {
            Double x1 = number(list.get(0));
            Double y1 = number(list.get(1));
            Double x2 = number(list.get(2));
            Double y2 = number(list.get(3));
            if (x1 != null && y1 != null && x2 != null && y2 != null) {
                return new double[] {Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)};
            }
        }
        return null;
    }

    private boolean samePage(NormalizedBlock previous, NormalizedBlock next) {
        if (previous.page() != null || next.page() != null) {
            return java.util.Objects.equals(previous.page(), next.page());
        }
        String previousSource = previous.sourceRef();
        String nextSource = next.sourceRef();
        return previousSource.isBlank() || nextSource.isBlank()
                || pageFromSourceRef(previousSource) == null
                || java.util.Objects.equals(pageFromSourceRef(previousSource), pageFromSourceRef(nextSource));
    }

    private Integer pageFromSourceRef(String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("page\\[(\\d+)]").matcher(sourceRef);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private NormalizedBlock tableBlock(ExtractedTable table, int order) {
        Map<String, Object> metadata = new LinkedHashMap<>(table.metadata());
        metadata.put("markdown", table.markdown());
        metadata.put("rowCount", table.rowCount());
        metadata.put("cellCount", table.cellCount());
        metadata.put("headerRowCount", table.headerRowCount());
        metadata.putIfAbsent("format", table.format());
        return NormalizedBlock.builder(NormalizedBlockType.TABLE, firstText(table.vectorText(), table.markdown()))
                .id(table.path())
                .sourceRef(table.sourceRef())
                .page(pageFromSourceRef(table.sourceRef()))
                .order(order)
                .blockIds(List.of(table.sourceRef()))
                .metadata(metadata)
                .build();
    }

    private NormalizedBlock imageBlock(ExtractedImage image, int order) {
        Map<String, Object> metadata = new LinkedHashMap<>(image.metadata());
        metadata.putIfAbsent("contentType", image.mimeType());
        metadata.putIfAbsent("filename", image.filename());
        metadata.putIfAbsent("width", image.width());
        metadata.putIfAbsent("height", image.height());
        String text = firstText(image.ocrText(), image.altText(), image.caption(), image.filename(), image.sourceRef());
        return NormalizedBlock.builder(NormalizedBlockType.IMAGE, text)
                .id(image.path())
                .sourceRef(image.sourceRef())
                .page(image.page())
                .slide(image.slide())
                .order(image.order() == null ? order : image.order())
                .parentBlockId(image.parentBlockId())
                .blockIds(image.sourceRefs())
                .confidence(image.confidence())
                .metadata(metadata)
                .build();
    }

    private void appendPageSearchContextBlocks(List<NormalizedBlock> blocks, int[] order, String documentId) {
        Map<Integer, List<NormalizedBlock>> byPage = new LinkedHashMap<>();
        for (NormalizedBlock block : blocks) {
            if (!block.hasText() || Boolean.TRUE.equals(block.metadata().get("searchContextOnly"))) {
                continue;
            }
            Integer page = block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page();
            if (page == null || page <= 0) {
                continue;
            }
            byPage.computeIfAbsent(page, ignored -> new ArrayList<>()).add(block);
        }
        for (Map.Entry<Integer, List<NormalizedBlock>> entry : byPage.entrySet()) {
            List<NormalizedBlock> pageBlocks = entry.getValue();
            String text = pageSearchText(entry.getKey(), pageBlocks);
            if (text.isBlank()) {
                continue;
            }
            List<String> sourceBlockIds = sourceBlockIds(pageBlocks);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("searchContextOnly", true);
            metadata.put("aggregationType", "PAGE_SEARCH_CONTEXT");
            metadata.put("sourceBlockCount", pageBlocks.size());
            metadata.put("sourceBlockIds", sourceBlockIds);
            blocks.add(NormalizedBlock.builder(NormalizedBlockType.PAGE, text)
                    .id(documentId + ":page:" + entry.getKey() + ":search-context")
                    .sourceRef("page[" + entry.getKey() + "]/search-context")
                    .page(entry.getKey())
                    .order(order[0]++)
                    .blockIds(sourceBlockIds)
                    .metadata(metadata)
                    .build());
        }
    }

    private List<String> sourceBlockIds(List<NormalizedBlock> blocks) {
        List<String> ids = new ArrayList<>();
        for (NormalizedBlock block : blocks) {
            if (block.blockIds().isEmpty()) {
                ids.add(block.id());
            } else {
                ids.addAll(block.blockIds());
            }
        }
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private String pageSearchText(int page, List<NormalizedBlock> blocks) {
        StringBuilder builder = new StringBuilder("Page ").append(page);
        for (NormalizedBlock block : blocks) {
            String text = block.text() == null ? "" : block.text().trim();
            if (text.isBlank()) {
                continue;
            }
            if (builder.length() + text.length() > 8000) {
                break;
            }
            builder.append('\n').append(text);
        }
        return builder.toString().trim();
    }

    private void putPostProcessingMetadata(Map<String, Object> metadata, List<NormalizedBlock> blocks) {
        long discardedNoise = blocks.stream()
                .filter(block -> Boolean.TRUE.equals(block.metadata().get("discardedOcrNoise")))
                .count();
        long mergedLines = blocks.stream()
                .filter(block -> Boolean.TRUE.equals(block.metadata().get("lineMergeApplied")))
                .count();
        long suppressedMath = blocks.stream()
                .filter(block -> Boolean.TRUE.equals(block.metadata().get("suppressedByMathCorrection")))
                .count();
        long discardedMathCorrection = blocks.stream()
                .filter(block -> "MATH_CORRECTION_NOISE".equals(block.metadata().get("discardReason")))
                .count();
        long duplicateMathCorrection = blocks.stream()
                .filter(block -> "DUPLICATE_MATH_CORRECTION".equals(block.metadata().get("discardReason")))
                .count();
        metadata.put("discardedOcrNoiseCount", discardedNoise);
        metadata.put("lineMergeAppliedCount", mergedLines);
        metadata.put("mathReplacementCount", suppressedMath);
        metadata.put("discardedMathCorrectionNoiseCount", discardedMathCorrection);
        metadata.put("deduplicatedMathCorrectionCount", duplicateMathCorrection);
        metadata.put("mathReplacementStrategy", suppressedMath > 0 ? "BBOX_OR_PAGE_WINDOW" : "NONE");
        metadata.putAll(documentQualitySummary(blocks));
        metadata.put("pageQuality", pageQuality(blocks));
        Object recommended = metadata.get("recommendedRoute");
        if (recommended == null && metadata.get("pdfRecommendedRoute") != null) {
            metadata.put("recommendedRoute", metadata.get("pdfRecommendedRoute"));
        }
        Object actual = metadata.get("actualRoute");
        if (actual == null && metadata.get("pdfActualRoute") != null) {
            metadata.put("actualRoute", metadata.get("pdfActualRoute"));
        }
        if (!metadata.containsKey("markdownQualityStatus")) {
            metadata.put("markdownQualityStatus", pageQualityStatus(blocks));
        }
    }

    private List<Map<String, Object>> pageQuality(List<NormalizedBlock> blocks) {
        Map<Integer, List<NormalizedBlock>> byPage = new LinkedHashMap<>();
        for (NormalizedBlock block : blocks) {
            if (!block.hasText() || hidden(block)) {
                continue;
            }
            Integer page = block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page();
            if (page != null && page > 0) {
                byPage.computeIfAbsent(page, ignored -> new ArrayList<>()).add(block);
            }
        }
        List<Map<String, Object>> pages = new ArrayList<>();
        for (Map.Entry<Integer, List<NormalizedBlock>> entry : byPage.entrySet()) {
            List<NormalizedBlock> pageBlocks = entry.getValue();
            long shortLines = pageBlocks.stream().filter(block -> shortFragment(block.text())).count();
            long mathBlocks = pageBlocks.stream().filter(block -> looksLikeBrokenMath(block.text())
                    || block.text().contains("$")).count();
            long lowQualityMath = pageBlocks.stream()
                    .filter(block -> looksLikeBrokenMath(block.text()))
                    .count();
            long spacingCandidates = pageBlocks.stream()
                    .filter(block -> koreanSpacingCandidate(block.text()))
                    .count();
            long tables = pageBlocks.stream()
                    .filter(block -> "TABLE".equals(block.metadata().get("contentRole")))
                    .count();
            long provenanceMissing = pageBlocks.stream()
                    .filter(block -> block.sourceRef() == null || block.sourceRef().isBlank())
                    .count();
            long noise = pageBlocks.stream()
                    .filter(block -> Boolean.TRUE.equals(block.metadata().get("discardedOcrNoise")))
                    .count();
            double shortRatio = pageBlocks.isEmpty() ? 0.0d : (double) shortLines / pageBlocks.size();
            double score = Math.max(0.0d, 1.0d
                    - (shortRatio >= 0.12d ? 0.25d : 0.0d)
                    - (shortRatio >= 0.20d ? 0.20d : 0.0d)
                    - (noise > 0 ? 0.15d : 0.0d)
                    - (lowQualityMath > 0 ? 0.20d : 0.0d)
                    - (spacingCandidates >= 3 ? 0.10d : 0.0d)
                    - (provenanceMissing > 0 ? 0.10d : 0.0d));
            Map<String, Object> quality = new LinkedHashMap<>();
            quality.put("page", entry.getKey());
            quality.put("blockCount", pageBlocks.size());
            quality.put("shortLineCount", shortLines);
            quality.put("shortLineRatio", shortRatio);
            quality.put("mathBlockCount", mathBlocks);
            quality.put("lowQualityMathBlockCount", lowQualityMath);
            quality.put("koreanSpacingCandidateCount", spacingCandidates);
            quality.put("tableBlockCount", tables);
            quality.put("discardedNoiseCount", noise);
            quality.put("provenanceMissingCount", provenanceMissing);
            quality.put("score", score);
            quality.put("status", score >= 0.85d ? "VALID" : "REVIEW_REQUIRED");
            quality.put("reprocessRecommended", lowQualityMath > 0 || score < 0.70d);
            quality.put("reprocessReason", lowQualityMath > 0 ? "LOW_QUALITY_MATH"
                    : score < 0.70d ? "LOW_PAGE_QUALITY" : null);
            pages.add(quality);
        }
        return pages;
    }

    private boolean koreanSpacingCandidate(String text) {
        if (text == null || text.isBlank() || looksLikeMathText(text)) {
            return false;
        }
        return text.matches(".*[가-힣]{10,}.*") && !text.matches(".*\\s[가-힣]{2,}.*");
    }

    private String pageQualityStatus(List<NormalizedBlock> blocks) {
        return pageQuality(blocks).stream()
                .anyMatch(page -> "REVIEW_REQUIRED".equals(page.get("status")))
                        ? "REVIEW_REQUIRED"
                        : "VALID";
    }

    private Map<String, Object> documentQualitySummary(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> contentBlocks = blocks.stream()
                .filter(NormalizedBlock::hasText)
                .filter(block -> !hidden(block))
                .toList();
        long shortLines = contentBlocks.stream()
                .filter(block -> shortFragment(block.text()))
                .count();
        long mathBlocks = contentBlocks.stream()
                .filter(block -> looksLikeMathText(block.text()))
                .count();
        double shortRatio = contentBlocks.isEmpty() ? 0.0d : (double) shortLines / contentBlocks.size();
        double score = Math.max(0.0d, 1.0d
                - Math.min(0.45d, shortRatio * 1.5d)
                - (mathBlocks == 0 ? 0.10d : 0.0d));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("markdownQualityTargetShortLineRatio", 0.20d);
        summary.put("markdownQualityTargetScore", 0.65d);
        summary.put("normalizedShortLineCount", shortLines);
        summary.put("normalizedShortLineRatio", shortRatio);
        summary.put("normalizedMathBlockCount", mathBlocks);
        summary.put("normalizedQualityScore", score);
        summary.put("markdownShortLineCount", shortLines);
        summary.put("markdownShortLineRatio", shortRatio);
        summary.put("markdownMathBlockCount", mathBlocks);
        summary.put("markdownQualityScore", score);
        return summary;
    }

    private String text(ParsedBlock block) {
        if (block.blockType() == BlockType.OCR_TEXT || Boolean.TRUE.equals(block.metadata().get("ocrApplied"))) {
            return OcrTextNormalizer.normalize(block.text());
        }
        return block.text();
    }

    private boolean ocrSource(ParsedBlock block) {
        return block != null
                && (block.blockType() == BlockType.OCR_TEXT || Boolean.TRUE.equals(block.metadata().get("ocrApplied")));
    }

    private NormalizedBlockType type(BlockType type, String text, Map<String, Object> metadata) {
        if (type == null) {
            return NormalizedBlockType.UNKNOWN;
        }
        if (type == BlockType.OCR_TEXT) {
            return classifyOcr(text, metadata);
        }
        return NormalizedBlockType.from(type.name());
    }

    private NormalizedBlockType classifyOcr(String text, Map<String, Object> metadata) {
        if (text == null || text.isBlank()) {
            return NormalizedBlockType.OCR_TEXT;
        }
        String trimmed = text.trim();
        if (LECTURE_HEADING.matcher(trimmed).matches() || headingLike(trimmed)) {
            metadata.putIfAbsent("level", 2);
            return NormalizedBlockType.HEADING;
        }
        if (PROBLEM_NUMBER.matcher(trimmed).matches() || trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            return NormalizedBlockType.LIST_ITEM;
        }
        return NormalizedBlockType.PARAGRAPH;
    }

    private boolean headingLike(String text) {
        int length = text.length();
        if (length < 2 || length > 28) {
            return false;
        }
        long hangul = text.chars().filter(ch -> ch >= 0xAC00 && ch <= 0xD7A3).count();
        long math = text.chars().filter(ch -> "0123456789=+-*/^".indexOf(ch) >= 0).count();
        return hangul >= 2 && math == 0 && !text.contains(".") && !text.endsWith("다");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
