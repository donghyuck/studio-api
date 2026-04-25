package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

public class TextractNormalizedDocumentAdapter {

    public NormalizedDocument adapt(String sourceDocumentId, ParsedFile parsedFile) {
        if (parsedFile == null) {
            return NormalizedDocument.builder(sourceDocumentId).build();
        }

        List<NormalizedBlock> blocks = new ArrayList<>();
        Map<String, ParsedBlock> tableBlocks = tableBlocks(parsedFile.blocks());
        Map<ParsedBlock, String> headingPaths = headingPaths(parsedFile.blocks());
        List<String> tableRefs = parsedFile.tables().stream()
                .map(ExtractedTable::sourceRef)
                .filter(ref -> ref != null && !ref.isBlank())
                .toList();
        parsedFile.blocks().stream()
                .filter(block -> !isExtractedTableBlock(block, tableRefs))
                .map(block -> fromBlock(block, headingPaths.get(block)))
                .forEach(blocks::add);
        parsedFile.tables().stream()
                .map(table -> {
                    ParsedBlock tableBlock = tableBlocks.get(table.sourceRef());
                    return fromTable(table, tableBlock, tableBlock == null ? "" : headingPaths.get(tableBlock));
                })
                .forEach(blocks::add);
        parsedFile.images().stream()
                .map(this::fromImage)
                .filter(NormalizedBlock::hasText)
                .forEach(blocks::add);

        return NormalizedDocument.builder(sourceDocumentId)
                .plainText(parsedFile.plainText())
                .sourceFormat(parsedFile.format().name())
                .filename(filename(parsedFile.metadata()))
                .blocks(blocks)
                .metadata(parsedFile.metadata())
                .build();
    }

    private Map<String, ParsedBlock> tableBlocks(List<ParsedBlock> blocks) {
        return blocks.stream()
                .filter(block -> block.blockType() == BlockType.TABLE)
                .filter(block -> block.sourceRef() != null && !block.sourceRef().isBlank())
                .collect(Collectors.toMap(
                        ParsedBlock::sourceRef,
                        Function.identity(),
                        this::firstByOrder,
                        LinkedHashMap::new));
    }

    private ParsedBlock firstByOrder(ParsedBlock left, ParsedBlock right) {
        return Comparator.comparing(
                ParsedBlock::order,
                Comparator.nullsLast(Integer::compareTo))
                .compare(left, right) <= 0 ? left : right;
    }

    private Map<ParsedBlock, String> headingPaths(List<ParsedBlock> blocks) {
        Map<ParsedBlock, String> paths = new HashMap<>();
        String current = "";
        // Heading context is inferred in document order, then looked up by block so
        // adapters can preserve parser output order without losing section context.
        for (ParsedBlock block : sortedBlocks(blocks)) {
            String explicit = stringMetadata(block.metadata(), NormalizedBlock.KEY_HEADING_PATH);
            if (isHeading(block)) {
                paths.put(block, "");
                current = firstNonBlank(explicit, block.text(), current);
                continue;
            }
            if (!explicit.isBlank()) {
                current = explicit;
            }
            paths.put(block, current);
        }
        return paths;
    }

    private List<ParsedBlock> sortedBlocks(List<ParsedBlock> blocks) {
        return blocks.stream()
                .sorted(Comparator.comparing(
                        ParsedBlock::order,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private boolean isExtractedTableBlock(ParsedBlock block, List<String> tableRefs) {
        return block.blockType() == BlockType.TABLE && tableRefs.contains(block.sourceRef());
    }

    private boolean isHeading(ParsedBlock block) {
        return block.blockType() == BlockType.TITLE || block.blockType() == BlockType.HEADING;
    }

    private NormalizedBlock fromBlock(ParsedBlock block, String headingPath) {
        return NormalizedBlock.builder(NormalizedBlockType.from(block.blockType().name()), block.text())
                .id(block.id())
                .sourceRef(block.sourceRef())
                .page(block.page())
                .slide(block.slide())
                .order(block.order())
                .parentBlockId(block.parentBlockId())
                .headingPath(firstNonBlank(stringMetadata(block.metadata(), NormalizedBlock.KEY_HEADING_PATH), headingPath))
                .blockIds(List.of(block.id()))
                .confidence(block.confidence())
                .metadata(block.metadata())
                .build();
    }

    private NormalizedBlock fromTable(ExtractedTable table, ParsedBlock tableBlock, String headingPath) {
        Map<String, Object> metadata = new LinkedHashMap<>(table.metadata());
        metadata.put(NormalizedBlock.KEY_ROW_COUNT, table.rowCount());
        metadata.put(NormalizedBlock.KEY_CELL_COUNT, table.cellCount());
        metadata.put(ExtractedTable.KEY_HEADER_ROW_COUNT, table.headerRowCount());
        String effectiveHeadingPath = firstNonBlank(
                stringMetadata(table.metadata(), NormalizedBlock.KEY_HEADING_PATH),
                headingPath);
        return NormalizedBlock.builder(NormalizedBlockType.TABLE, table.vectorText())
                .id(table.path())
                .sourceRef(table.sourceRef())
                .page(tableBlock == null ? null : tableBlock.page())
                .slide(tableBlock == null ? null : tableBlock.slide())
                .order(tableBlock == null ? null : tableBlock.order())
                .parentBlockId(tableBlock == null ? null : tableBlock.parentBlockId())
                .headingPath(effectiveHeadingPath)
                .blockIds(tableCellRefs(table, tableBlock))
                .confidence(tableBlock == null ? null : tableBlock.confidence())
                .metadata(metadata)
                .build();
    }

    private NormalizedBlock fromImage(ExtractedImage image) {
        String text = firstNonBlank(image.caption(), image.altText(), image.ocrText());
        Map<String, Object> metadata = new LinkedHashMap<>(image.metadata());
        metadata.put("mimeType", image.mimeType());
        metadata.put("filename", image.filename());
        metadata.put("width", image.width());
        metadata.put("height", image.height());
        return NormalizedBlock.builder(image.ocrApplied() ? NormalizedBlockType.OCR_TEXT : NormalizedBlockType.IMAGE_CAPTION, text)
                .id(image.path())
                .sourceRef(image.sourceRef())
                .page(image.page())
                .slide(image.slide())
                .order(image.order())
                .parentBlockId(image.parentBlockId())
                .headingPath(stringMetadata(image.metadata(), NormalizedBlock.KEY_HEADING_PATH))
                .blockIds(image.sourceRefs().isEmpty() ? List.of(image.path()) : image.sourceRefs())
                .confidence(image.confidence())
                .metadata(metadata)
                .build();
    }

    private List<String> tableCellRefs(ExtractedTable table, ParsedBlock tableBlock) {
        List<String> refs = table.cells().stream()
                .map(ExtractedTableCell::sourceRef)
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .toList();
        if (!refs.isEmpty()) {
            return refs;
        }
        if (tableBlock != null && tableBlock.id() != null && !tableBlock.id().isBlank()) {
            return List.of(tableBlock.id());
        }
        return table.path() == null || table.path().isBlank() ? List.of() : List.of(table.path());
    }

    private String filename(Map<String, Object> metadata) {
        Object value = metadata.get("filename");
        return value instanceof String stringValue ? stringValue : "";
    }

    private String stringMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof String stringValue ? stringValue : "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
