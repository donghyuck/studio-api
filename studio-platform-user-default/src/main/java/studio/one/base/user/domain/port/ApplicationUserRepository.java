package studio.one.base.user.domain.port;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.model.ApplicationGroup;
import studio.one.base.user.domain.model.ApplicationUser;
import studio.one.base.user.domain.model.UserIdOnly;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationUserRepository {

    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:repository:user-repository";

    Page<ApplicationUser> findAll(Pageable pageable);

    Optional<ApplicationUser> findById(Long userId);

    Optional<ApplicationUser> findEnabledById(Long userId);

    Optional<ApplicationUser> findByUsername(String username);

    Optional<ApplicationUser> findEnabledByUsername(String username);

    Optional<ApplicationUser> findByUsernameForUpdate(String username);

    Optional<UserIdOnly> findFirstByUsernameIgnoreCase(String username);

    Optional<ApplicationUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Page<ApplicationUser> findUsersByGroupId(Long groupId, Pageable pageable);

    List<ApplicationUser> findUsersByGroupId(Long groupId);

    default Page<ApplicationUser> findUsersByCompanyId(Long companyId, Pageable pageable) {
        throw new UnsupportedOperationException("Company-scoped user listing is not supported");
    }

    default Page<ApplicationUser> searchByCompanyId(Long companyId, String keyword, Pageable pageable) {
        throw new UnsupportedOperationException("Company-scoped user search is not supported");
    }

    Page<ApplicationUser> search(String keyword, Pageable pageable);

    List<ApplicationGroup> findGroupsByUserId(Long userId);

    List<Long> findGroupIdsByUserId(Long userId);

    ApplicationUser save(ApplicationUser user);

    void delete(ApplicationUser user);
}
