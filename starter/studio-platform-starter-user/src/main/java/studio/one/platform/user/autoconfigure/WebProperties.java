package studio.one.platform.user.autoconfigure;

import javax.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = PropertyKeys.Features.User.Web.PREFIX)
public class WebProperties {

    private boolean enabled = false; 

    /** 공통 베이스 경로 (예: /api/mgmt) */
    @NotBlank
    private String basePath = "/api/mgmt";

    @NotBlank
    private String selfPath = "/api/self";

    private final Endpoints endpoints = new Endpoints();

    private final Self self = new Self();   

    @Getter
    @Setter
    public static class Self {
        
        private boolean enabled = true; 
        
        @NotBlank
        private String path = "/api/self";
    }

    @Getter
    @Setter
    public static class Endpoints {
        private Toggle user = new Toggle();
        private Toggle group = new Toggle();
        private Toggle role = new Toggle();
        private Toggle company = new Toggle();
    }

    @Getter
    @Setter
    public static class Toggle {
        private boolean enabled = true;
    }

    /** / 중복 방지용 */
    public String normalizedBasePath() {
        String p = this.basePath.trim();
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }
}
