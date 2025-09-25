package studio.echo.platform.security.autoconfigure;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import studio.echo.platform.constant.PropertyKeys;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = PropertyKeys.Security.Audit.PREFIX)
public class AuditProperties {

    @Valid
    private LoginFailure loginFailure = new LoginFailure();

    @Getter
    @Setter
    public static class LoginFailure {
        /** 기능 온/오프 */
        private boolean enabled = true;

        /** 비동기 저장 여부 */
        private boolean async = true;

        /** 보관 주기(일). null/<=0 이면 미사용 */
        @PositiveOrZero
        private Integer retentionDays = 180;

        /** 프록시 환경에서 클라이언트 IP를 뽑을 헤더 (null이면 사용 안 함) */
        private String captureIpHeader = "X-Forwarded-For";

        /** User-Agent 저장 여부 */
        private boolean captureUserAgent = true;

        /** 저장 샘플링 비율 (0.0 ~ 1.0) */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double sampling = 1.0;
    }
}
