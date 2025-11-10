package studio.one.base.user.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupRole;
import studio.one.base.user.domain.entity.ApplicationGroupRoleId;
import studio.one.base.user.domain.entity.ApplicationGroupWithMemberCount;
import studio.one.base.user.domain.entity.ApplicationRole;

public interface ApplicationGroupRoleRepository {

    List<ApplicationRole> findRolesByGroupId(Long groupId);

    List<ApplicationGroup> findGroupsByRoleId(Long roleId);

    boolean existsByGroupIdAndRoleId(Long groupId, Long roleId);

    void deleteByGroupId(Long groupId);

    void deleteByRoleId(Long roleId);

    int deleteByGroupIdAndRoleIds(Long groupId, Collection<Long> roleIds);

    int deleteByGroupIdsAndRoleId(Collection<Long> groupIds, Long roleId);

    Page<ApplicationGroup> findGroupsByRoleId(Long roleId, String keyword, Pageable pageable);

    Page<ApplicationGroupWithMemberCount> findGroupsWithMemberCountByRoleId(Long roleId, String keyword, Pageable pageable);

    ApplicationGroupRole save(ApplicationGroupRole groupRole);

    <S extends ApplicationGroupRole> List<S> saveAll(Iterable<S> groupRoles);

    void deleteById(ApplicationGroupRoleId id);
}
