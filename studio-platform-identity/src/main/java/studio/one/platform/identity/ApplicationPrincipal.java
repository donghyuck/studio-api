package studio.one.platform.identity;

import java.util.Set;
import java.util.Optional;

public interface ApplicationPrincipal {

    Long getUserId(); // nullable 허용 가능(익명/외부계정 등)

    String getUsername(); // nullable 허용 가능(서비스계정 등)

    Set<String> getRoles(); // "ADMIN", "MODERATOR", "USER" 같은 roleName 권장

    default Optional<Long> userId() {
        return Optional.ofNullable(getUserId());
    }

    default Optional<String> username() {
        return Optional.ofNullable(getUsername());
    }

    default boolean hasRole(String role) {
        return getRoles() != null && getRoles().contains(role);
    }
}
