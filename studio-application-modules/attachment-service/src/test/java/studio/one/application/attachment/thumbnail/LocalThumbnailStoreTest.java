package studio.one.application.attachment.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalThumbnailStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAppliesOwnerOnlyPermissionsWhenPosixIsSupported() throws Exception {
        assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null);
        LocalThumbnailStore store = new LocalThumbnailStore(tempDir.toString());
        ThumbnailKey key = new ThumbnailKey(2001, 10L, 128, "png");

        String savedPath = store.save(key, new ByteArrayInputStream(new byte[] {1, 2, 3}));

        Path file = Path.of(savedPath);
        Path dir = file.getParent();
        assertThat(Files.getPosixFilePermissions(dir))
                .isEqualTo(Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        assertThat(Files.getPosixFilePermissions(file))
                .isEqualTo(Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE));
    }
}
