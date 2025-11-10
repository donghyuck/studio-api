package studio.one.base.user.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.entity.ApplicationUserRole;
import studio.one.base.user.domain.entity.ApplicationUserRoleId;

public interface ApplicationUserRoleRepository {

    List<ApplicationUserRole> findAllByUserId(Long userId);

    Page<ApplicationUserRole> findAllByUserId(Long userId, Pageable pageable);

    List<ApplicationUserRole> findAllByRoleId(Long roleId);

    Page<ApplicationUserRole> findAllByRoleId(Long roleId, Pageable pageable);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    int deleteByUserIdAndRoleId(Long userId, Long roleId);

    int deleteByUserIdAndRoleIds(Long userId, Collection<Long> roleIds);

    int deleteByUserIdsAndRoleId(Collection<Long> userIds, Long roleId);

    Page<ApplicationRole> findRolesByUserId(Long userId, Pageable pageable);

    List<ApplicationRole> findRolesByUserId(Long userId);

    List<Long> findRoleIdsByUserId(Long userId);

    Page<ApplicationUser> findUsersByRoleId(Long roleId, Pageable pageable);

    List<ApplicationUser> findUsersByRoleId(Long roleId);

    Page<ApplicationUser> findUsersByRoleId(Long roleId, String keyword, Pageable pageable);

    Page<ApplicationUser> findUsersByRoleIdViaGroup(Long roleId, String keyword, Pageable pageable);

    ApplicationUserRole save(ApplicationUserRole userRole);

    <S extends ApplicationUserRole> List<S> saveAll(Iterable<S> userRoles);

    boolean existsById(ApplicationUserRoleId id ); 
    
}
