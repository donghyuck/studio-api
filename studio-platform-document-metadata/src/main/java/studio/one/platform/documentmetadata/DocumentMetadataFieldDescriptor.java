package studio.one.platform.documentmetadata;

public record DocumentMetadataFieldDescriptor(
        String fieldId,
        String label,
        String description,
        boolean required,
        boolean recommended,
        boolean multiValued,
        ValueType valueType) {

    public enum ValueType {
        TEXT,
        PARTIAL_DATE,
        IDENTIFIER,
        LONG_TEXT
    }
}
