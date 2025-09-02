package studio.echo.base.user.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.echo.base.user.domain.entity.ApplicationUser;
import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.domain.model.User;

public interface ApplicationUserService<T extends User> {
    
    public static final String SERVICE_NAME = "components:application-user-service";

    Page<ApplicationUser> findAll(Pageable pageable);

    User get(Long userId);

    Optional<T> findByUsername(String username);

    ApplicationUser create(ApplicationUser user);

    ApplicationUser update(Long userId, Consumer<ApplicationUser> mutator);

    void delete(Long userId);

    void enable(Long userId, String actor);

    void disable(Long userId,
                    String actor,
                    String reason,
                    OffsetDateTime until,
                    boolean revokeTokens,
                    boolean invalidateSessions,
                    boolean notifyUser);

    // paging & search
    Page<ApplicationUser> search(String q, Pageable pageable);

    Page<ApplicationUser> getUsersByGroup(Long groupId, Pageable pageable);

    // associations
    void assignRole(Long userId, Long roleId, String by);

    void revokeRole(Long userId, Long roleId);

    void joinGroup(Long userId, Long groupId, String by);

    void leaveGroup(Long userId, Long groupId);

    // effective roles
    Set<Role> findEffectiveRoles(Long userId);
}
