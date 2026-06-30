package studio.one.platform.chunking.service;

import java.util.List;
import java.util.Map;

public class BlockifyTypedFieldValidator {

    public String validate(BlockifyDocumentType documentType, BlockifyBlock block, String sourceText) {
        if (block == null) {
            return "ANSWER_HAS_NO_BODY";
        }
        BlockifyDocumentType type = documentType == null ? BlockifyDocumentType.GENERAL : documentType;
        return switch (type) {
            case POLICY -> validatePolicy(block, sourceText);
            case NARRATIVE -> validateNarrative(block, sourceText);
            case TECHNICAL -> validateGroundedFields(block, sourceText,
                    List.of("endpoint", "command", "configurationKey", "errorCode"));
            case TABLE_HEAVY -> validateGroundedFields(block, sourceText,
                    List.of("rowKey", "columnKey", "metricValue", "unit"));
            case MANUAL -> validateGroundedFields(block, sourceText,
                    List.of("taskName", "input", "output", "warning", "nextAction"));
            case AUTO, GENERAL -> null;
        };
    }

    private String validatePolicy(BlockifyBlock block, String sourceText) {
        Map<String, Object> fields = fields(block);
        if (!hasAny(fields, "condition", "obligation", "prohibition", "exception", "deadline")) {
            return null;
        }
        return validateGroundedFields(block, sourceText,
                List.of("articleNo", "condition", "obligation", "prohibition", "exception", "deadline"));
    }

    private String validateNarrative(BlockifyBlock block, String sourceText) {
        Map<String, Object> fields = fields(block);
        if (!hasAny(fields, "event", "character", "cause", "effect", "quote")) {
            return null;
        }
        return validateGroundedFields(block, sourceText,
                List.of("character", "event", "cause", "effect", "location", "quote"));
    }

    private String validateGroundedFields(BlockifyBlock block, String sourceText, List<String> keys) {
        Map<String, Object> fields = fields(block);
        String normalizedSource = normalize(sourceText);
        for (String key : keys) {
            Object value = fields.get(key);
            if (!(value instanceof String text) || text.isBlank()) {
                continue;
            }
            String normalized = normalize(text);
            if (normalized.length() >= 12 && !normalizedSource.contains(normalized)) {
                return "TYPED_FIELD_NOT_GROUNDED";
            }
        }
        return null;
    }

    private Map<String, Object> fields(BlockifyBlock block) {
        return block == null || block.typedFields() == null ? Map.of() : block.typedFields();
    }

    private boolean hasAny(Map<String, Object> fields, String... keys) {
        if (fields == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            Object value = fields.get(key);
            if (value != null && !value.toString().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
