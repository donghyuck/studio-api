package studio.echo.platform.autoconfigure.jpa.auditor;

import java.util.Optional;

public class SecurityAuditorAware implements org.springframework.data.domain.AuditorAware<String> {
  @Override public java.util.Optional<String> getCurrentAuditor() {
    var ctx = org.springframework.security.core.context.SecurityContextHolder.getContext();
    var auth = (ctx != null) ? ctx.getAuthentication() : null;
    if (auth == null || !auth.isAuthenticated()) return Optional.of("anonymous");
    return Optional.ofNullable(auth.getName());
  }
}
