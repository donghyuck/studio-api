package studio.one.base.user.persistence.jpa;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationGroupMembership;
import studio.one.base.user.domain.entity.ApplicationGroupMembershipId;
import studio.one.base.user.persistence.ApplicationGroupMembershipRepository;

@Repository
public interface ApplicationGroupMembershipJpaRepository
    extends JpaRepository<ApplicationGroupMembership, ApplicationGroupMembershipId>, ApplicationGroupMembershipRepository {

  @Override
  @Query("select gm.id.groupId as groupId, " +
      "count(gm) as count " +
      "from ApplicationGroupMembership gm " +
      "where gm.id.groupId in :groupIds " +
      "group by gm.id.groupId")
  List<GroupCount> countMembersByGroupIds(@Param("groupIds") Collection<Long> groupIds);

  @Override
  @Query(value = "select gm from ApplicationGroupMembership gm where gm.group.groupId = :groupId",
      countQuery = "select count(gm) from ApplicationGroupMembership gm where gm.group.groupId = :groupId")
  Page<ApplicationGroupMembership> findAllByGroupId(@Param("groupId") Long groupId, Pageable pageable);

  @Override
  @Query(value = "select gm from ApplicationGroupMembership gm where gm.id.userId = :userId",
      countQuery = "select count(gm) from ApplicationGroupMembership gm where gm.id.userId = :userId")
  Page<ApplicationGroupMembership> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

  @Override
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from ApplicationGroupMembership m " +
      "where m.group.groupId = :groupId and m.id.userId in :userIds")
  int deleteByGroupIdAndUserIds(@Param("groupId") Long groupId, @Param("userIds") Collection<Long> userIds);

  @Override
  @Query("select m.id.userId " +
      "from ApplicationGroupMembership m " +
      "where m.group.groupId = :groupId and m.id.userId in :userIds")
  List<Long> findExistingUserIdsInGroup(@Param("groupId") Long groupId,
      @Param("userIds") Collection<Long> userIds);

  @Override
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "insert into TB_APPLICATION_GROUP_MEMBERS (group_id, user_id, joined_at, joined_by) " +
      " select :groupId, " +
      " uid, " +
      " coalesce(:joinedAt, now()), " +
      " :joinedBy " +
      " from unnest(cast(:userIds as bigint[])) as uid " +
      " on conflict (group_id, user_id) do nothing ", nativeQuery = true)
  int insertIgnoreConflicts(@Param("groupId") Long groupId,
      @Param("userIds") Long[] userIds,
      @Param("joinedAt") LocalDateTime joinedAt,
      @Param("joinedBy") String joinedBy);
}
