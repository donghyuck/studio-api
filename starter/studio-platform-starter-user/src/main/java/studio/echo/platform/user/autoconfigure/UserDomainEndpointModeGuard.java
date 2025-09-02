package studio.echo.platform.user.autoconfigure;

import java.util.Locale;

import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.echo.platform.security.authz.EndpointModeGuard;

@RequiredArgsConstructor
public class UserDomainEndpointModeGuard implements EndpointModeGuard {

    private final WebProperties web;

    @Override
    public boolean allows(String domain, String action) {
        if (!StringUtils.hasText(domain))
            return true;
        String d = domain.trim().toLowerCase(Locale.ROOT);
        WebProperties.Toggle t = resolveToggle(d);
        if (t == null)
            return true;

        if (!t.isEnabled())
            return false; // enabled=false → 전면 차단
        WebProperties.Mode m = t.getMode() != null ? t.getMode() : WebProperties.Mode.CRUD;
        String a = normalizeAction(action);

        switch (m) {
            case DISABLED:
                return false;
            case READ_ONLY:
                return "read".equals(a);
            case CRUD:
            default:
                return true;
        }
    }

    private WebProperties.Toggle resolveToggle(String d) {
        if (web == null || web.getEndpoints() == null)
            return null;
        switch (d) {
            case "user":
                return web.getEndpoints().getUser();
            case "group":
                return web.getEndpoints().getGroup();
            case "role":
                return web.getEndpoints().getRole();
            case "company":
                return web.getEndpoints().getCompany();
            default:
                return null;
        }
    }

    private String normalizeAction(String action) {
        if (action == null)
            return "read";
        switch (action.toLowerCase(Locale.ROOT)) {
            case "access":
            case "view":
            case "read":
                return "read";
            case "manage":
            case "write":
                return "write";
            case "admin":
                return "admin";
            default:
                return action.toLowerCase(Locale.ROOT);
        }
    }
}
