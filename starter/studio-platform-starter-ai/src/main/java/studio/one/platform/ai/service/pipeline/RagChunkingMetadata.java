package studio.one.platform.ai.service.pipeline;

final class RagChunkingMetadata {

    private RagChunkingMetadata() {
    }

    static String normalizeObjectScope(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
