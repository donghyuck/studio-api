package studio.one.platform.chunking.service;

public record BlockifyDocumentTypeClassification(
        BlockifyDocumentType requestedType,
        BlockifyDocumentType detectedType,
        double confidence,
        BlockifyDocumentSignals signals,
        String reason) {

    public BlockifyDocumentType effectiveType() {
        return requestedType == null || requestedType == BlockifyDocumentType.AUTO ? detectedType : requestedType;
    }
}
