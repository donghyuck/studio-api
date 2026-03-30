package studio.one.platform.identity;

import java.util.Optional;

public interface IdentityService {

    /**
     * @deprecated Use {@link IdentityConstants#SERVICE_NAME} instead.
     */
    @Deprecated
    String SERVICE_NAME = IdentityConstants.SERVICE_NAME;

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
