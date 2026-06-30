package studio.one.platform.chunking.service;

public record BlockifyProfile(
        BlockifyDocumentType documentType,
        String profileId,
        String schemaVersion,
        String questionStyle) {
}
