package studio.one.base.user.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import studio.one.base.user.domain.entity.ApplicationRole;

public interface ApplicationRoleRepository {

    Page<ApplicationRole> findAll(Pageable pageable);

    List<ApplicationRole> findAll(Sort sort);

    Optional<ApplicationRole> findById(Long roleId);

    Optional<ApplicationRole> findByName(String name);

    boolean existsByName(String name);

    List<ApplicationRole> findRolesByUserId(Long userId);

    Page<ApplicationRole> findRolesByUserId(Long userId, Pageable pageable);

    List<ApplicationRole> findRolesByGroupIds(Collection<Long> groupIds);

    Page<ApplicationRole> findRolesByGroupId(Long groupId, Pageable pageable);

    List<ApplicationRole> findRolesByGroupId(Long groupId, Sort sort);

    default List<ApplicationRole> findRolesByGroupId(Long groupId) {
        return findRolesByGroupId(groupId, Sort.by("name").ascending());
    }

    List<ApplicationRole> findEffectiveRolesByUserId(Long userId);

    List<Long> findExistingIds(Collection<Long> ids);

    ApplicationRole save(ApplicationRole role);

    void delete(ApplicationRole role);

    void deleteById(Long roleId);
}
