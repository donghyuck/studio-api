package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
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
        parsedFile.blocks().stream()
                .map(this::fromBlock)
                .forEach(blocks::add);
        parsedFile.tables().stream()
                .map(this::fromTable)
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

    private NormalizedBlock fromTable(ExtractedTable table) {
        Map<String, Object> metadata = new LinkedHashMap<>(table.metadata());
        metadata.put("rowCount", table.rowCount());
        metadata.put("cellCount", table.cellCount());
        metadata.put("headerRowCount", table.headerRowCount());
        return NormalizedBlock.builder(NormalizedBlockType.TABLE, table.vectorText())
                .id(table.path())
                .sourceRef(table.sourceRef())
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
