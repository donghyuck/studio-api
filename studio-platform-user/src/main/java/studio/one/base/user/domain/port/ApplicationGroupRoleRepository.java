package studio.one.base.user.domain.port;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.model.ApplicationGroup;
import studio.one.base.user.domain.model.ApplicationGroupRole;
import studio.one.base.user.domain.model.ApplicationGroupRoleId;
import studio.one.base.user.domain.model.ApplicationGroupWithMemberCount;
import studio.one.base.user.domain.model.ApplicationRole;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationGroupRoleRepository {

    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:repository:group-role-repository";

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
