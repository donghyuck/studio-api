package studio.one.application.avatar.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import studio.one.application.avatar.domain.entity.AvatarImage;
import studio.one.base.user.domain.model.User;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.mediaio.ImageSource;
import studio.one.platform.mediaio.ImageSources;

/**
 * Avatar(프로필 이미지) 서비스 인터페이스 - 개선 버전
 *
 * 개선 포인트
 * - 메서드 네이밍 정리(get → find, load 등) 및 오타 수정
 * - null 반환 대신 Optional 사용
 * - 카운트는 Integer → long
 * - 업로드 입력(파일/스트림)을 ImageSource로 일원화 + 편의 default 메서드 제공
 * - 썸네일 파라미터를 ThumbnailOptions로 캡슐화(크기/포맷/자르기 등 확장)
 * - "대표 이미지"는 primary 로 명확히 표현
 * - ID/username 기반 조회 모두 지원
 *
 * @param <U> 사용자 타입 (도메인 User 엔티티)
 */

public interface AvatarImageService<U extends User> {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":application-avatar-service";
  

    /*
     * ===============================
     * Query
     * ===============================
     */
    /** 해당 사용자의 모든 아바타 메타 목록(최신순 등 구현체 정책에 따름) */
    List<AvatarImage> findAllByUser(U user);

    /** 해당 사용자의 총 아바타 개수 */
    long countByUser(U user);

    /** 아바타 ID로 메타 조회 */
    Optional<AvatarImage> findById(Long avatarImageId);

    /** username으로 대표 아바타 메타 조회 */
    Optional<AvatarImage> findPrimaryByUsername(String username);

    /** 사용자 기준 대표(primary) 아바타 메타 조회 */
    Optional<AvatarImage> findPrimaryByUser(U user);

    /** 아바타 원본 데이터 스트림 (호출 측에서 반드시 close 필요) */
    Optional<InputStream> openDataStream(AvatarImage image) throws IOException;

    /** 썸네일 스트림(호출 측에서 close 필요). 옵션 미지정 시 합리적 기본값 적용. */
    Optional<InputStream> openThumbnailStream(AvatarImage image, ThumbnailOptions options) throws IOException;

    /*
     * ===============================
     * Command
     * ===============================
     */
    AvatarImage upload(AvatarImage meta, ImageSource source, U actor) throws IOException;

    default AvatarImage upload(AvatarImage meta, Path file, U actor) throws IOException {
        try (ImageSource src = ImageSources.of(file)) {
            return upload(meta, src, actor);
        }
    }

    AvatarImage replaceData(AvatarImage image, ImageSource source) throws IOException;

    void setPrimary(AvatarImage image);

    void remove(AvatarImage image);

    final class ThumbnailOptions {
        public enum Fit {
            COVER, CONTAIN, FILL, INSIDE, OUTSIDE
        }

        private final int width ;
        private final int height;
        private final int quality;
        private final Fit fit;
        private final String format;

        private ThumbnailOptions(Builder b) {
            this.width = b.width;
            this.height = b.height;
            this.fit = b.fit;
            this.format = b.format;
            this.quality = b.quality;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public Fit fit() {
            return fit;
        }

        public String format() {
            return format;
        }

        public int quality() {
            return quality;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private int width, height, quality = 85;
            private Fit fit = Fit.CONTAIN;
            private String format = null;

            public Builder width(int v) {
                this.width = v;
                return this;
            }

            public Builder height(int v) {
                this.height = v;
                return this;
            }

            public Builder fit(Fit v) {
                this.fit = v;
                return this;
            }

            public Builder format(String v) {
                this.format = v;
                return this;
            }

            public Builder quality(int v) {
                this.quality = v;
                return this;
            }

            public ThumbnailOptions build() {
                if (width <= 0 || height <= 0)
                    throw new IllegalArgumentException("width/height > 0");
                return new ThumbnailOptions(this);
            }
        }
    }
}
