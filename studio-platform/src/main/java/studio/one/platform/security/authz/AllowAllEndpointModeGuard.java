package studio.one.platform.security.authz;

/**
 * {@link EndpointModeGuard} implementation that allows every domain/action
 * combination. Used as the default guard when no domain specific restrictions
 * are provided by feature modules.
 */
public class AllowAllEndpointModeGuard implements EndpointModeGuard {

    @Override
    public boolean allows(String domain, String action) {
        return true;
    }
}
