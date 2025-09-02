package studio.echo.base.user.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.echo.base.user.domain.entity.ApplicationGroupMembership;
import studio.echo.base.user.domain.entity.ApplicationGroupMembershipId;

@Repository
public interface ApplicationGroupMembershipRepository extends JpaRepository<ApplicationGroupMembership, ApplicationGroupMembershipId> {

    @Query(
      value = "select gm from ApplicationGroupMembership gm where gm.group.groupId = :groupId",
      countQuery = "select count(gm) from ApplicationGroupMembership gm where gm.group.groupId = :groupId"
    )
    Page<ApplicationGroupMembership> findAllByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    @Query(
      value = "select gm from ApplicationGroupMembership gm where gm.user.userId = :userId",
      countQuery = "select count(gm) from ApplicationGroupMembership gm where gm.user.userId = :userId"
    )
    Page<ApplicationGroupMembership> findAllByUserId(@Param("userId") Long userId, Pageable pageable);
}
