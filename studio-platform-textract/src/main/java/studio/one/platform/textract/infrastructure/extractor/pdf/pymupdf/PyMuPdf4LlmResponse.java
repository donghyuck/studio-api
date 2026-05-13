package studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class PyMuPdf4LlmResponse {
    String filename;
    String contentType;
    String markdown;
    List<Page> pages;
    List<Block> blocks;
    List<Table> tables;
    List<Image> images;
    Map<String, Object> metadata;
    List<Warning> warnings;
    Long elapsedMs;
    Boolean ocrApplied;


    public PyMuPdf4LlmResponse(String filename, String contentType, String markdown, List<Page> pages, List<Block> blocks, List<Table> tables, List<Image> images, Map<String, Object> metadata, List<Warning> warnings, Long elapsedMs, Boolean ocrApplied) {
        pages = copyList(pages);
        blocks = copyList(blocks);
        tables = copyList(tables);
        images = copyList(images);
        metadata = copyMap(metadata);
        warnings = copyList(warnings);


        this.filename = filename;


        this.contentType = contentType;


        this.markdown = markdown;


        this.pages = pages;


        this.blocks = blocks;


        this.tables = tables;


        this.images = images;


        this.metadata = metadata;


        this.warnings = warnings;


        this.elapsedMs = elapsedMs;


        this.ocrApplied = ocrApplied;


    }

        @Value
    @Accessors(fluent = true)
    public static final class Page {
        Integer pageNumber;
        String text;
        List<Block> blocks;
        Map<String, Object> metadata;

        public Page(Integer pageNumber, String text, List<Block> blocks, Map<String, Object> metadata) {
            blocks = copyList(blocks);
            metadata = copyMap(metadata);

            this.pageNumber = pageNumber;

            this.text = text;

            this.blocks = blocks;

            this.metadata = metadata;

        }
    }

        @Value
    @Accessors(fluent = true)
    public static final class Block {
        String type;
        String text;
        Integer pageNumber;
        Integer order;
        Integer level;
        String sourceRef;
        List<Double> bbox;
        Map<String, Object> metadata;

        public Block(String type, String text, Integer pageNumber, Integer order, Integer level, String sourceRef, List<Double> bbox, Map<String, Object> metadata) {
            bbox = copyList(bbox);
            metadata = copyMap(metadata);

            this.type = type;

            this.text = text;

            this.pageNumber = pageNumber;

            this.order = order;

            this.level = level;

            this.sourceRef = sourceRef;

            this.bbox = bbox;

            this.metadata = metadata;

        }
    }

        @Value
    @Accessors(fluent = true)
    public static final class Table {
        Integer pageNumber;
        String caption;
        List<String> headers;
        List<List<String>> rows;
        String markdown;
        String sourceRef;
        List<Double> bbox;
        Map<String, Object> metadata;

        public Table(Integer pageNumber, String caption, List<String> headers, List<List<String>> rows, String markdown, String sourceRef, List<Double> bbox, Map<String, Object> metadata) {
            headers = copyList(headers);
            rows = rows == null ? List.of() : rows.stream()
                    .map(PyMuPdf4LlmResponse::copyList)
                    .collect(Collectors.toList());
            bbox = copyList(bbox);
            metadata = copyMap(metadata);

            this.pageNumber = pageNumber;

            this.caption = caption;

            this.headers = headers;

            this.rows = rows;

            this.markdown = markdown;

            this.sourceRef = sourceRef;

            this.bbox = bbox;

            this.metadata = metadata;

        }
    }

        @Value
    @Accessors(fluent = true)
    public static final class Image {
        Integer pageNumber;
        String name;
        String mimeType;
        Integer width;
        Integer height;
        String sourceRef;
        String caption;
        String altText;
        String ocrText;
        Boolean ocrApplied;
        List<Double> bbox;
        Map<String, Object> metadata;

        public Image(Integer pageNumber, String name, String mimeType, Integer width, Integer height, String sourceRef, String caption, String altText, String ocrText, Boolean ocrApplied, List<Double> bbox, Map<String, Object> metadata) {
            bbox = copyList(bbox);
            metadata = copyMap(metadata);

            this.pageNumber = pageNumber;

            this.name = name;

            this.mimeType = mimeType;

            this.width = width;

            this.height = height;

            this.sourceRef = sourceRef;

            this.caption = caption;

            this.altText = altText;

            this.ocrText = ocrText;

            this.ocrApplied = ocrApplied;

            this.bbox = bbox;

            this.metadata = metadata;

        }
    }

        @Value
    @Accessors(fluent = true)
    public static final class Warning {
        String code;
        String message;
        String sourceRef;
        Map<String, Object> metadata;

        public Warning(String code, String message, String sourceRef, Map<String, Object> metadata) {
            metadata = copyMap(metadata);

            this.code = code;

            this.message = message;

            this.sourceRef = sourceRef;

            this.metadata = metadata;

        }
    }

    private static <T> List<T> copyList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
