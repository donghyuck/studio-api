package studio.one.application.avatar.replica;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.util.FileSystemUtils;

import lombok.RequiredArgsConstructor;
import studio.echo.platform.constant.ServiceNames;

@RequiredArgsConstructor
public class FileReplicaStore {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":application-avatar-file-replica-service";
    public static final String DEFAULT_IMAGE_FILE_DIR = "images";

    private final Path baseDir;

    public Path userDir(long userId) {
        return baseDir.resolve("user").resolve(Long.toString(userId));
    }

    public Path imageDir(long userId, long imageId) {
        return userDir(userId).resolve("img").resolve(Long.toString(imageId));
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String extFromNameOrType(String fileName, String contentType) {
        if (fileName != null) {
            String n = fileName.toLowerCase();
            for (String e : new String[] { ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp" }) {
                if (n.endsWith(e))
                    return e.substring(1);
            }
        }
        if (contentType != null) {
            if (contentType.contains("png"))
                return "png";
            if (contentType.contains("webp"))
                return "webp";
            if (contentType.contains("gif"))
                return "gif";
            if (contentType.contains("bmp"))
                return "bmp";
            if (contentType.contains("jpeg") || contentType.contains("jpg"))
                return "jpg";
        }
        return "jpg";
    }

    public void ensureDirs(Path dir) throws IOException {
        if (Files.notExists(dir))
            Files.createDirectories(dir);
    }

    /** 원자적 파일 쓰기 (tmp → move) */
    public Path writeAtomic(Path target, byte[] bytes) throws IOException {
        ensureDirs(target.getParent());
        Path tmp = target.getParent().resolve(target.getFileName() + ".tmp-" + System.nanoTime());
        Files.write(tmp, bytes, StandardOpenOption.CREATE_NEW);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        touch(target);
        return target;
    }

    public void touch(Path p) {
        try {
            Files.setLastModifiedTime(p, FileTime.from(Instant.now()));
        } catch (Exception ignored) {
        }
    }

    public Optional<InputStream> openIfExists(Path p) throws IOException {
        if (Files.exists(p)) {
            touch(p);
            return Optional.of(Files.newInputStream(p, StandardOpenOption.READ));
        }
        return Optional.empty();
    }

    public void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
    }

    public void deleteTreeQuietly(Path dir) {
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (Exception ignored) {
        }
    }

    /** 파일 복사(스트림→파일), 큰 파일도 대응 */
    public Path copy(InputStream is, Path target) throws IOException {
        ensureDirs(target.getParent());
        Path tmp = target.getParent().resolve(target.getFileName() + ".tmp-" + System.nanoTime());
        try (FileChannel out = FileChannel.open(tmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1)
                out.write(java.nio.ByteBuffer.wrap(buf, 0, r));
        }
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        touch(target);
        return target;
    }

}
