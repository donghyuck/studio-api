package studio.one.application.attachment.storage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files; 
import java.nio.file.Path;
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
            Path dir = Path.of(baseDir, String.valueOf(attachment.getObjectType()));
            Path file = dir.resolve(attachment.getAttachmentId() + "");
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new RuntimeException("File not found", e);
        }
    }

    @Override
    public void delete(Attachment attachment) {
        try {
            Path dir = Path.of(baseDir, String.valueOf(attachment.getObjectType()));
            Path file = dir.resolve(attachment.getAttachmentId() + "");
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }
}
