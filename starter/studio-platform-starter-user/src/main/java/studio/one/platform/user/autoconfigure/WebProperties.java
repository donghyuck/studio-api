package studio.one.platform.user.autoconfigure;

import java.util.Collections;
import java.util.List;

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

    /** 공통 베이스 경로 (예: /api/mgmt) */
    @NotBlank
    private String basePath = "/api/mgmt";

    @NotBlank
    private String selfPath = "/api/self";

    private Mode mode = Mode.CRUD;

    private final Roles roles = new Roles();

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
        private Mode mode = Mode.CRUD;
        private Roles roles;
    }

    @Getter
    @Setter
    public static class Roles {
        private List<String> read = List.of("ADMIN", "MANAGER");
        private List<String> write = List.of("ADMIN");
    }

    public enum Mode {
        READ_ONLY, CRUD, DISABLED
    }

    /** / 중복 방지용 */
    public String normalizedBasePath() {
        String p = this.basePath.trim();
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    public Toggle endpoint(String name) {
        if (name == null)
            return null;
        String key = name.trim().toLowerCase();
        switch (key) {
            case "user":
                return endpoints.getUser();
            case "group":
                return endpoints.getGroup();
            case "role":
                return endpoints.getRole();
            case "company":
                return endpoints.getCompany();
            default:
                return null;
        }
    }

    public Mode effectiveMode(String component) {
        Toggle t = endpoint(component);
        if (t != null && t.getMode() != null)
            return t.getMode();
        return (this.mode != null) ? this.mode : Mode.CRUD;
    }

    public Roles effectiveRoles(String component) {
        Toggle t = endpoint(component);
        if (t != null && t.getRoles() != null) return t.getRoles();
        return (this.roles != null) ? this.roles : new Roles();
    }

    public boolean isEnabled(String component) {
        Toggle t = endpoint(component);
        return t != null && t.isEnabled() && effectiveMode(component) != Mode.DISABLED;
    }

    public boolean isWriteAllowed(String component) {
        Mode m = effectiveMode(component);
        return m == Mode.CRUD;
    }

    public List<String> requiredRoles(String component, boolean write) {
        Roles r = effectiveRoles(component);
        List<String> out = write ? r.getWrite() : r.getRead();
        return (out != null) ? out : Collections.emptyList();
    }
}
