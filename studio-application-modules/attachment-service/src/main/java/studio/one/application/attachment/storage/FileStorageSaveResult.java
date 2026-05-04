package studio.one.application.attachment.storage;

import java.util.Map;

public record FileStorageSaveResult(String location, Map<String, String> properties) {

    public FileStorageSaveResult {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static FileStorageSaveResult of(String location) {
        return new FileStorageSaveResult(location, Map.of());
    }
}
