package studio.one.application.attachment.infrastructure.storage;

import java.util.Locale;
import java.util.Optional;

public enum AttachmentStorageType {
    filesystem,
    database,
    objectstorage;

    public static Optional<AttachmentStorageType> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
