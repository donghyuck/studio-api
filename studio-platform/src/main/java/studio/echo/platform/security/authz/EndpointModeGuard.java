package studio.echo.platform.security.authz;

public interface EndpointModeGuard {

    boolean allows(String domain, String action);

}
