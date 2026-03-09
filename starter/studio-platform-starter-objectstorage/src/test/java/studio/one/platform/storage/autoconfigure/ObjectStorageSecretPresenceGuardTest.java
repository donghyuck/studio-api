package studio.one.platform.storage.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ObjectStorageSecretPresenceGuardTest {

    @Test
    void validateRejectsMissingS3SecretKey() {
        StorageProperties properties = new StorageProperties();
        StorageProperties.Provider provider = new StorageProperties.Provider();
        provider.setEnabled(true);
        provider.setType("s3");
        provider.getCredentials().setAccessKey("access");
        provider.getCredentials().setSecretKey(" ");
        properties.getProviders().put("main", provider);

        ObjectStorageSecretPresenceGuard guard = new ObjectStorageSecretPresenceGuard(properties);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsFsProviderWithRoot() {
        StorageProperties properties = new StorageProperties();
        StorageProperties.Provider provider = new StorageProperties.Provider();
        provider.setEnabled(true);
        provider.setType("fs");
        provider.getFs().setRoot("/tmp/storage");
        properties.getProviders().put("local", provider);

        ObjectStorageSecretPresenceGuard guard = new ObjectStorageSecretPresenceGuard(properties);

        assertDoesNotThrow(guard::validate);
    }
}
