package studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PyMuPdf4LlmResponse(
        String filename,
        String contentType,
        String markdown,
        List<Page> pages,
        List<Block> blocks,
        List<Table> tables,
        List<Image> images,
        Map<String, Object> metadata,
        List<Warning> warnings,
        Long elapsedMs,
        Boolean ocrApplied) {

    public PyMuPdf4LlmResponse {
        pages = copyList(pages);
        blocks = copyList(blocks);
        tables = copyList(tables);
        images = copyList(images);
        metadata = copyMap(metadata);
        warnings = copyList(warnings);
    }

    public record Page(
            Integer pageNumber,
            String text,
            List<Block> blocks,
            Map<String, Object> metadata) {
        public Page {
            blocks = copyList(blocks);
            metadata = copyMap(metadata);
        }
    }

    public record Block(
            String type,
            String text,
            Integer pageNumber,
            Integer order,
            Integer level,
            String sourceRef,
            List<Double> bbox,
            Map<String, Object> metadata) {
        public Block {
            bbox = copyList(bbox);
            metadata = copyMap(metadata);
        }
    }

    public record Table(
            Integer pageNumber,
            String caption,
            List<String> headers,
            List<List<String>> rows,
            String markdown,
            String sourceRef,
            List<Double> bbox,
            Map<String, Object> metadata) {
        public Table {
            headers = copyList(headers);
            rows = rows == null ? List.of() : rows.stream()
                    .map(PyMuPdf4LlmResponse::copyList)
                    .toList();
            bbox = copyList(bbox);
            metadata = copyMap(metadata);
        }
    }

    public record Image(
            Integer pageNumber,
            String name,
            String mimeType,
            Integer width,
            Integer height,
            String sourceRef,
            String caption,
            String altText,
            String ocrText,
            Boolean ocrApplied,
            List<Double> bbox,
            Map<String, Object> metadata) {
        public Image {
            bbox = copyList(bbox);
            metadata = copyMap(metadata);
        }
    }

    public record Warning(
            String code,
            String message,
            String sourceRef,
            Map<String, Object> metadata) {
        public Warning {
            metadata = copyMap(metadata);
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
