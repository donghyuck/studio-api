package studio.one.application.avatar.autoconfigure;

import javax.validation.Valid;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.echo.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.echo.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".avatar-image")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Validated
public class AvatarFeatureProperties extends FeatureToggle {

    private Api web = new Api();

    @Valid
    private Replica replica = new Replica();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Api {
        private String userBase = "/api/mgmt/users";
        /** 공개(permitAll) prefix */
        private String publicBase = "/api/users";
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Replica {
        /**
         * 로컬 파일 레플리카 기본 경로 (예: /var/lib/app/avatars)
         */
        private String baseDir;
        /**
         * 시작 시 디렉터리 생성 여부
         */
        private boolean ensureDirs = true;
        /**
         * 청소 기능 활성화
         */
        private Cleanup cleanup = new Cleanup();

    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Cleanup {
        private boolean enabled = true;
        private String cron = "0 0 3 * * *";
        private int ttlDays = 30;
    }
}
