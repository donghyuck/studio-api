package studio.one.application.attachment.infrastructure.storage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files; 
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.model.Attachment;

@RequiredArgsConstructor
public class LocalFileStore implements FileStorage {

    private final String baseDir;

    @Override
    public String save(Attachment attachment, InputStream in) {
        try {
            Path dir = Path.of(baseDir, String.valueOf(attachment.getObjectType()));
            Files.createDirectories(dir);
            Path file = dir.resolve(attachment.getAttachmentId() + "");
            Files.copy(in, file);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Local file save failed", e);
        }
    }

    @Override
    public InputStream load(Attachment attachment) {
        try {
            Path file = resolvePath(attachment);
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new RuntimeException("File not found", e);
        }
    }

    @Override
    public void delete(Attachment attachment) {
        try {
            Path preferred = pathFor(attachment.getObjectType(), attachment.getAttachmentId());
            Files.deleteIfExists(preferred);
            Optional<Path> legacy = findByAttachmentId(attachment.getAttachmentId());
            if (legacy.isPresent() && !legacy.get().equals(preferred)) {
                Files.deleteIfExists(legacy.get());
            }
        } catch (IOException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    private Path resolvePath(Attachment attachment) throws IOException {
        Path preferred = pathFor(attachment.getObjectType(), attachment.getAttachmentId());
        if (Files.isRegularFile(preferred)) {
            return preferred;
        }
        Optional<Path> legacy = findByAttachmentId(attachment.getAttachmentId());
        if (legacy.isPresent()) {
            return legacy.get();
        }
        return preferred;
    }

    private Path pathFor(long objectType, long attachmentId) {
        return Path.of(baseDir, String.valueOf(objectType)).resolve(String.valueOf(attachmentId));
    }

    private Optional<Path> findByAttachmentId(long attachmentId) throws IOException {
        Path root = Path.of(baseDir);
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        String fileName = String.valueOf(attachmentId);
        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve(fileName))
                    .filter(Files::isRegularFile)
                    .findFirst();
        }
    }
}
