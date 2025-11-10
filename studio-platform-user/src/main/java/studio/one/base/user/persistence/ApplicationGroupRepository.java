package studio.one.base.user.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupWithMemberCount;

public interface ApplicationGroupRepository {

    Page<ApplicationGroup> findAll(Pageable pageable);

    Optional<ApplicationGroup> findById(Long groupId);

    List<ApplicationGroup> findAll();

    Page<ApplicationGroup> findGroupsByUserId(Long userId, Pageable pageable);

    List<ApplicationGroup> findGroupsByUserId(Long userId);

    List<ApplicationGroupWithMemberCount> findGroupsWithMemberCountByUserId(Long userId);

    Page<ApplicationGroup> findGroupsByName(String keyword, Pageable pageable);

    Page<ApplicationGroupWithMemberCount> findGroupsWithMemberCountByName(String keyword, Pageable pageable);

    ApplicationGroup save(ApplicationGroup group);

    void delete(ApplicationGroup group);

    void deleteById(Long groupId);

    boolean existsByName(String name);
    
}
