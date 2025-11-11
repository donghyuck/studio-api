package studio.one.application.avatar.service.impl;

import static studio.one.platform.mediaio.util.BytesUtil.count;
import static studio.one.platform.mediaio.util.BytesUtil.readAll;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.avatar.domain.entity.AvatarImage;
import studio.one.application.avatar.domain.entity.AvatarImageData;
import studio.one.application.avatar.persistence.AvatarImageDataRepository;
import studio.one.application.avatar.persistence.AvatarImageRepository;
import studio.one.application.avatar.service.AvatarImageService;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.platform.mediaio.ImageSource;

@Slf4j
@RequiredArgsConstructor
@Transactional
public class AvatarImageServiceImpl implements AvatarImageService<User> {

    private final AvatarImageRepository imageRepo;
    private final AvatarImageDataRepository dataRepo;
    private final ApplicationUserService<User, Role> userService;

    /* ---------- Query ---------- */
    private long getUserId(User user) {
        if (user == null)
            throw new IllegalArgumentException("user must not be null");
        Long id = user.getUserId();
        if (id != null && id > 0L)
            return id;
        String username = StringUtils.trimToEmpty(user.getUsername());
        if (StringUtils.isEmpty(username))
            throw new IllegalArgumentException("userId is null/invalid and username is empty");
        return userService.findIdByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvatarImage> findAllByUser(User user) { 
        Long userId = getUserId(user);
        return imageRepo.findByUserIdOrderByCreationDateDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUser(User user) {
        Long userId = getUserId(user);
        return imageRepo.countByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AvatarImage> findById(Long id) {
        return imageRepo.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AvatarImage> findPrimaryByUser(User user) {
        Long userId = getUserId(user);
        return imageRepo.findFirstByUserIdAndPrimaryImageTrueOrderByCreationDateDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AvatarImage> findPrimaryByUsername(String username) {
        return Optional.ofNullable(username)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(userService::findByUsername)
                .map(u -> u.getUserId())
                .flatMap(imageRepo::findFirstByUserIdAndPrimaryImageTrueOrderByCreationDateDesc);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InputStream> openDataStream(AvatarImage image) throws IOException {
        AvatarImage e = requireEntity(image.getId());
        AvatarImageData data = e.getData();
        if (data == null || data.getData() == null)
            return Optional.empty();
        return Optional.of(new java.io.ByteArrayInputStream(data.getData()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InputStream> openThumbnailStream(AvatarImage image, ThumbnailOptions options) throws IOException {

        return Optional.empty();
    }

    /* ---------- Command ---------- */

    @Override
    public AvatarImage upload(AvatarImage metaLike, ImageSource source, User actor) throws IOException {
        Objects.requireNonNull(metaLike, "meta");
        Objects.requireNonNull(source, "source");

        // 메타 구성
        AvatarImage meta = new AvatarImage();
        meta.setUserId(metaLike.getUserId());
        meta.setPrimaryImage(Boolean.TRUE.equals(metaLike.isPrimaryImage()));
        meta.setFileName(nvl(metaLike.getFileName(), source.fileName()));
        meta.setContentType(nvl(metaLike.getContentType(), source.contentType()));

        long size = source.size();
        if (size < 0)
            size = count(source.openStream()); // ✅ 공통 유틸
        meta.setFileSize(size >= 0 ? size : null);

        AvatarImage saved = imageRepo.save(meta);

        // 바이너리 저장(@MapsId)
        byte[] bytes = readAll(source.openStream()); // ✅ 공통 유틸
        AvatarImageData blob = new AvatarImageData();
        blob.setAvatarImage(saved);
        blob.setId(saved.getId());
        blob.setData(bytes);
        saved.setData(blob);

        // 대표 유일성 보장
        if (Boolean.TRUE.equals(saved.isPrimaryImage())) {
            unsetOthersPrimary(saved.getUserId(), saved.getId());
        }

        dataRepo.save(blob);
        return imageRepo.save(saved);
    }

    @Override
    public AvatarImage replaceData(AvatarImage imageMeta, ImageSource source) throws IOException {
        Objects.requireNonNull(imageMeta, "imageMeta");
        Objects.requireNonNull(source, "source");

        AvatarImage entity = requireEntity(imageMeta.getId());

        byte[] bytes = readAll(source.openStream()); // ✅ 공통 유틸
        AvatarImageData data = entity.getData();
        if (data == null) {
            data = new AvatarImageData();
            data.setAvatarImage(entity);
            data.setId(entity.getId());
            entity.setData(data);
        }
        data.setData(bytes);

        // 메타 갱신
        if (source.fileName() != null && !source.fileName().isBlank())
            entity.setFileName(source.fileName());
        if (source.contentType() != null && !source.contentType().isBlank())
            entity.setContentType(source.contentType());
        if (source.size() >= 0)
            entity.setFileSize(source.size());

        dataRepo.save(data);
        return imageRepo.save(entity);
    }

    @Override
    public void setPrimary(AvatarImage imageMeta) {
        AvatarImage e = requireEntity(imageMeta.getId());
        if (!Boolean.TRUE.equals(e.isPrimaryImage())) {
            e.setPrimaryImage(true);
            unsetOthersPrimary(e.getUserId(), e.getId());
            imageRepo.save(e);
        }
    }

    @Override
    public void remove(AvatarImage imageMeta) {
        AvatarImage e = requireEntity(imageMeta.getId());
        boolean wasPrimary = Boolean.TRUE.equals(e.isPrimaryImage());
        Long userId = e.getUserId();

        imageRepo.delete(e);

        if (wasPrimary) {
            imageRepo.findByUserIdOrderByCreationDateDesc(userId).stream().findFirst().ifPresent(latest -> {
                latest.setPrimaryImage(true);
                imageRepo.save(latest);
            });
        }
    }

    /* ---------- Helpers ---------- */

    private AvatarImage requireEntity(Long id) {
        return imageRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Avatar not found: " + id));
    }

    private void unsetOthersPrimary(Long userId, Long keepId) {
        var all = imageRepo.findByUserIdOrderByCreationDateDesc(userId);
        boolean changed = false;
        for (AvatarImage it : all) {
            if (!it.getId().equals(keepId) && Boolean.TRUE.equals(it.isPrimaryImage())) {
                it.setPrimaryImage(false);
                changed = true;
            }
        }
        if (changed)
            imageRepo.saveAll(all);
    }

    private static String nvl(String a, String b) {
        return (a != null && !a.isBlank()) ? a : ((b != null && !b.isBlank()) ? b : null);
    }
}