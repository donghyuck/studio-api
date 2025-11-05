package studio.one.application.avatar.service.impl;

import static studio.one.application.avatar.replica.FileReplicaStore.sha256Hex;
import static studio.one.platform.mediaio.util.BytesUtil.readAll;
import static studio.one.platform.mediaio.util.ImageResize.resize;
import static studio.one.platform.mediaio.util.MediaTypeUtil.extFromNameOrType;
import static studio.one.platform.mediaio.util.MediaTypeUtil.guessWriteFormat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.application.avatar.domain.entity.AvatarImage;
import studio.one.application.avatar.replica.FileReplicaStore; // ← 패키지 확인
import studio.one.application.avatar.service.AvatarImageService;
import studio.one.application.avatar.service.AvatarImageService.ThumbnailOptions;
import studio.one.base.user.domain.model.User;
import studio.one.platform.mediaio.ImageSource;
import studio.one.platform.mediaio.util.ImageResize.Fit;

// 캐시 파일명 규칙 유틸(아바타 도메인 전용 규칙이라 이 클래스에 포함)
final class ReplicaPaths {
    
    static Path original(FileReplicaStore store, AvatarImage img, String shaNullable) {
        long userId = img.getUserId();
        long imageId = img.getId();
        String ext = extFromNameOrType(img.getFileName(), img.getContentType());
        String sha = (shaNullable == null || shaNullable.isBlank()) ? "plain" : shaNullable;
        return store.imageDir(userId, imageId).resolve("original-" + sha + "." + ext);
    }

    static Path thumb(FileReplicaStore store, AvatarImage img, ThumbnailOptions opt, String shaNullable) {
        long userId = img.getUserId();
        long imageId = img.getId();
        String fmt = (opt.format() != null && !opt.format().isBlank())
                ? opt.format()
                : extFromNameOrType(img.getFileName(), img.getContentType());
        String sha = (shaNullable == null || shaNullable.isBlank()) ? "plain" : shaNullable;
        return store.imageDir(userId, imageId)
                .resolve(String.format("thumb-%dx%d-%s-%s.%s",
                        opt.width(), opt.height(), opt.fit(), sha, fmt));
    }
}

/**
 * 파일 시스템 레플리카 캐시 데코레이터
 * - 읽기: 캐시 → DB → 캐시 생성
 * - 쓰기: DB 후 캐시 동기화(Write-Through)
 */
@RequiredArgsConstructor
@Transactional
public class AvatarImageFilesystemReplicaService implements AvatarImageService<User> {
    // ↑ User 타입 경로는 실제 프로젝트의 User 클래스로 변경하세요.

    private final AvatarImageService<User> delegate;
    private final FileReplicaStore replicas;

    /* ---------- Query (delegate + cache) ---------- */

    @Override
    @Transactional(readOnly = true)
    public List<AvatarImage> findAllByUser(User user) {
        return delegate.findAllByUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUser(User user) {
        return delegate.countByUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AvatarImage> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AvatarImage> findPrimaryByUser(User user) {
        return delegate.findPrimaryByUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AvatarImage> findPrimaryByUsername(String username) {
        return delegate.findPrimaryByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InputStream> openDataStream(AvatarImage image) throws IOException {
        // 1) 캐시 hit
        var cached = replicas.openIfExists(ReplicaPaths.original(replicas, image, null));
        if (cached.isPresent())
            return cached;

        // 2) miss → DB → 캐시 생성
        var raw = delegate.openDataStream(image);
        if (raw.isEmpty())
            return Optional.empty();

        byte[] bytes = readAll(raw.get());
        String sha = sha256Hex(bytes);
        replicas.writeAtomic(ReplicaPaths.original(replicas, image, sha), bytes);
        return Optional.of(new ByteArrayInputStream(bytes));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InputStream> openThumbnailStream(AvatarImage image, ThumbnailOptions options) throws IOException {
        var thumb = replicas.openIfExists(ReplicaPaths.thumb(replicas, image, options, null));
        if (thumb.isPresent())
            return thumb;

        var rawOpt = openDataStream(image); // 원본 확보(캐시 우선)
        if (rawOpt.isEmpty())
            return Optional.empty();

        BufferedImage src = ImageIO.read(rawOpt.get());
        if (src == null)
            return Optional.empty();

        BufferedImage dst = resize(src, options.width(), options.height(),
                Fit.valueOf(options.fit().name())); // enum 이름만 맞추면 매핑 OK

        ByteArrayOutputStream bos = new ByteArrayOutputStream(16 * 1024);
        String fmt = (options.format() != null && !options.format().isBlank())
                ? options.format()
                : guessWriteFormat(image.getContentType(), image.getFileName());
        ImageIO.write(dst, fmt, bos);
        byte[] bytes = bos.toByteArray();

        replicas.writeAtomic(ReplicaPaths.thumb(replicas, image, options, "plain"), bytes);
        return Optional.of(new ByteArrayInputStream(bytes));
    }

    /* ---------- Command (Write-Through) ---------- */

    @Override
    public AvatarImage upload(AvatarImage meta, ImageSource source, User actor) throws IOException {
        AvatarImage saved = delegate.upload(meta, source, actor);

        var data = delegate.openDataStream(saved).orElse(null);
        if (data != null) {
            byte[] bytes = readAll(data);
            String sha = sha256Hex(bytes);
            replicas.writeAtomic(ReplicaPaths.original(replicas, saved, sha), bytes);
        }
        return saved;
    }

    @Override
    public AvatarImage replaceData(AvatarImage image, ImageSource source) throws IOException {
        AvatarImage saved = delegate.replaceData(image, source);

        // 캐시 비움 후 재생성
        Path dir = replicas.imageDir(saved.getUserId(), saved.getId());
        replicas.deleteTreeQuietly(dir);

        var data = delegate.openDataStream(saved).orElse(null);
        if (data != null) {
            byte[] bytes = readAll(data);
            String sha = sha256Hex(bytes);
            replicas.writeAtomic(ReplicaPaths.original(replicas, saved, sha), bytes);
        }
        return saved;
    }

    @Override
    public void setPrimary(AvatarImage image) {
        delegate.setPrimary(image); // 캐시는 영향 없음
    }

    @Override
    public void remove(AvatarImage image) {
        delegate.remove(image);
        Path dir = replicas.imageDir(image.getUserId(), image.getId());
        replicas.deleteTreeQuietly(dir);
    }

    /* ---------- (선택) 오래된 파일 청소 ---------- */

    @Scheduled(cron = "0 15 3 * * *")
    public void cleanupOldReplicas() {
        try {
            // baseDir 추정: FileReplicaStore.userDir(0)/user/.. 구조를 쓴다면 상위 상위가 baseDir
            Path anyUserDir = replicas.userDir(0L);
            Path base = anyUserDir.getParent() != null ? anyUserDir.getParent().getParent() : null;
            if (base == null)
                return;

            final long expireSec = 90L * 24 * 3600;
            Files.walk(base)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            long last = Files.getLastModifiedTime(p).toMillis() / 1000;
                            if (Instant.now().getEpochSecond() - last > expireSec) {
                                Files.deleteIfExists(p);
                            }
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }
}
