package studio.one.platform.autoconfigure;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = PropertyKeys.Persistence.Jpa.Auditing.PREFIX)
public class JpaAuditingProperties {

    private boolean enabled = false;
    private ClockProps clock = new ClockProps();
    private AuditorProps auditor = new AuditorProps();
    private FallbackProps fallback = new FallbackProps();

    @Getter
    @Setter
    public static class ClockProps {
        private String zoneId = "UTC";
    }

    @Getter
    @Setter
    public static class AuditorProps {
        /** security | header | fixed | composite */
        private String strategy = "security";
        private String header = "X-Actor";
        private String fixed = "system";
        private List<String> composite = java.util.Arrays.asList("security", "header", "fixed");
    }

    @Getter
    @Setter
    public static class FallbackProps {
        private boolean prePersist = true;
        private boolean preUpdate = true;
    }
}
