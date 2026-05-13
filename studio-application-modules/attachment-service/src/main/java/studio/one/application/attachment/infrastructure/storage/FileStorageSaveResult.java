package studio.one.application.attachment.infrastructure.storage;

import java.util.Map;

public class FileStorageSaveResult {

    private final String location;
    private final Map<String, String> properties;

    public FileStorageSaveResult(
            String location,
            Map<String, String> properties) {
        this.location = location;
        this.properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public String location() { return location; }

    public Map<String, String> properties() { return properties; }


    public static FileStorageSaveResult of(String location) {
        return new FileStorageSaveResult(location, Map.of());
    }
}
