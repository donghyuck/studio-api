package studio.one.application.attachment.thumbnail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocalThumbnailStore implements ThumbnailStorage {

    private final String baseDir;

    @Override
    public String save(ThumbnailKey key, InputStream input) {
        try {
            Path dir = resolveDir(key);
            Files.createDirectories(dir);
            applyOwnerOnlyDirectoryPermissions(dir);
            Path file = dir.resolve(fileName(key));
            Files.copy(input, file);
            applyOwnerOnlyFilePermissions(file);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Local thumbnail save failed", e);
        }
    }

    @Override
    public InputStream load(ThumbnailKey key) {
        try {
            Path file = resolveDir(key).resolve(fileName(key));
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new RuntimeException("Thumbnail not found", e);
        }
    }

    @Override
    public void delete(ThumbnailKey key) {
        try {
            Path file = resolveDir(key).resolve(fileName(key));
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Thumbnail delete failed", e);
        }
    }

    @Override
    public void deleteAll(int objectType, long attachmentId) {
        Path dir = Path.of(baseDir, String.valueOf(objectType), String.valueOf(attachmentId));
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.list(dir)) {
            paths.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Thumbnail deleteAll failed", e);
        }
    }

    private Path resolveDir(ThumbnailKey key) {
        return Path.of(baseDir, String.valueOf(key.getObjectType()), String.valueOf(key.getAttachmentId()));
    }

    private String fileName(ThumbnailKey key) {
        return key.getSize() + "." + key.getFormat();
    }

    private void applyOwnerOnlyDirectoryPermissions(Path dir) throws IOException {
        if (Files.getFileAttributeView(dir, PosixFileAttributeView.class) == null) {
            return;
        }
        Files.setPosixFilePermissions(dir, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
    }

    private void applyOwnerOnlyFilePermissions(Path file) throws IOException {
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) == null) {
            return;
        }
        Files.setPosixFilePermissions(file, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
    }
}
