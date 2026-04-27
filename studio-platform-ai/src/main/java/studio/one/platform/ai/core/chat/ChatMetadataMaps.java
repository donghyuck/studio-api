package studio.one.platform.ai.core.chat;

import java.util.LinkedHashMap;
import java.util.Map;

final class ChatMetadataMaps {

    private ChatMetadataMaps() {
    }

    static Map<String, Object> compact(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> compact = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                return;
            }
            if (value instanceof String stringValue && stringValue.isBlank()) {
                return;
            }
            if (value instanceof Map<?, ?> mapValue && mapValue.isEmpty()) {
                return;
            }
            compact.put(key, value);
        });
        return Map.copyOf(compact);
    }
}
