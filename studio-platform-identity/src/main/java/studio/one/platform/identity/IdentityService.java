package studio.one.platform.identity;

import java.util.Optional;

public interface IdentityService {

    public static final String SERVICE_NAME = "features:identity:identity-service";

    Optional<UserRef> findById(Long userId);

    Optional<UserRef> findByUsername(String username);

    default Optional<UserRef> resolve(UserKey key) {
        if (key instanceof UserIdKey k)
            return findById(k.userId());
        if (key instanceof UsernameKey k)
            return findByUsername(k.username());
        return Optional.empty();
    }
}
