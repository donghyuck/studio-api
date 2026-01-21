package studio.one.base.user.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.entity.ApplicationGroupMembership;
import studio.one.base.user.domain.entity.ApplicationGroupMembershipId;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationGroupMembershipRepository {
public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:repository:group-membership-repository";

  interface GroupCount {

    Long getGroupId();

    long getCount();
  }

  Page<ApplicationGroupMembership> findAll(Pageable pageable);

  Page<ApplicationGroupMembership> findAllByGroupId(Long groupId, Pageable pageable);

  Page<ApplicationGroupMembership> findAllByUserId(Long userId, Pageable pageable);

  Page<Long> findUserIdsByGroupId(Long groupId, Pageable pageable);

  List<GroupCount> countMembersByGroupIds(Collection<Long> groupIds);

  int deleteByGroupIdAndUserIds(Long groupId, Collection<Long> userIds);

  List<Long> findExistingUserIdsInGroup(Long groupId, Collection<Long> userIds);

  int insertIgnoreConflicts(Long groupId, Long[] userIds, LocalDateTime joinedAt, String joinedBy);

  boolean existsById(ApplicationGroupMembershipId id);

  ApplicationGroupMembership save(ApplicationGroupMembership membership);

  <S extends ApplicationGroupMembership> List<S> saveAll(Iterable<S> memberships);

  void deleteById(ApplicationGroupMembershipId id);
  
}
