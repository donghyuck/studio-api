package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.Comparator;
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
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

public class TextractNormalizedDocumentAdapter {

    public NormalizedDocument adapt(String sourceDocumentId, ParsedFile parsedFile) {
        if (parsedFile == null) {
            return NormalizedDocument.builder(sourceDocumentId).build();
        }

        List<NormalizedBlock> blocks = new ArrayList<>();
        Map<String, ParsedBlock> tableBlocks = tableBlocks(parsedFile.blocks());
        List<String> tableRefs = parsedFile.tables().stream()
                .map(ExtractedTable::sourceRef)
                .filter(ref -> ref != null && !ref.isBlank())
                .toList();
        parsedFile.blocks().stream()
                .filter(block -> !isExtractedTableBlock(block, tableRefs))
                .map(this::fromBlock)
                .forEach(blocks::add);
        parsedFile.tables().stream()
                .map(table -> fromTable(table, tableBlocks.get(table.sourceRef())))
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

    private boolean isExtractedTableBlock(ParsedBlock block, List<String> tableRefs) {
        return block.blockType() == BlockType.TABLE && tableRefs.contains(block.sourceRef());
    }

    private NormalizedBlock fromBlock(ParsedBlock block) {
        return NormalizedBlock.builder(NormalizedBlockType.from(block.blockType().name()), block.text())
                .id(block.id())
                .sourceRef(block.sourceRef())
                .page(block.page())
                .slide(block.slide())
                .order(block.order())
                .parentBlockId(block.parentBlockId())
                .metadata(block.metadata())
                .build();
    }

    private NormalizedBlock fromTable(ExtractedTable table, ParsedBlock tableBlock) {
        Map<String, Object> metadata = new LinkedHashMap<>(table.metadata());
        metadata.put(NormalizedBlock.KEY_ROW_COUNT, table.rowCount());
        metadata.put(NormalizedBlock.KEY_CELL_COUNT, table.cellCount());
        metadata.put(ExtractedTable.KEY_HEADER_ROW_COUNT, table.headerRowCount());
        return NormalizedBlock.builder(NormalizedBlockType.TABLE, table.vectorText())
                .id(table.path())
                .sourceRef(table.sourceRef())
                .page(tableBlock == null ? null : tableBlock.page())
                .slide(tableBlock == null ? null : tableBlock.slide())
                .order(tableBlock == null ? null : tableBlock.order())
                .parentBlockId(tableBlock == null ? null : tableBlock.parentBlockId())
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
                .metadata(metadata)
                .build();
    }

    private String filename(Map<String, Object> metadata) {
        Object value = metadata.get("filename");
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
