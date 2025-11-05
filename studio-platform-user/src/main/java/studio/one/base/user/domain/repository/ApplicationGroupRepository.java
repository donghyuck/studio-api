package studio.one.base.user.domain.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupWithMemberCount;

@Repository
public interface ApplicationGroupRepository extends JpaRepository<ApplicationGroup, Long> {

        // 사용자 → 그룹 (Page)
        @Query(value = "select g from ApplicationGroup g " +
                        "join ApplicationGroupMembership gm on gm.group = g " +
                        "where gm.user.userId = :userId", countQuery = "select count(g) from ApplicationGroup g " +
                                        "join ApplicationGroupMembership gm on gm.group = g " +
                                        "where gm.user.userId = :userId")
        Page<ApplicationGroup> findGroupsByUserId(@Param("userId") Long userId, Pageable pageable);

        // 사용자 → 그룹 (List)
        @Query("select g from ApplicationGroup g " +
                        "join ApplicationGroupMembership gm on gm.group = g " +
                        "where gm.user.userId = :userId")
        List<ApplicationGroup> findGroupsByUserId(@Param("userId") Long userId);

        @Query(
                "select g.groupId as groupId, " +
                "        g.name    as name, " +
                "        count(allm) as memberCount " +
                "from ApplicationGroup g " +
                "join ApplicationGroupMembership gm " +
                "on gm.group = g and gm.user.userId = :userId " +
                "left join ApplicationGroupMembership allm " +
                "on allm.group = g " +
                "group by g.groupId, g.name " +
                "order by g.groupId desc " )
        List<ApplicationGroupWithMemberCount> findGroupsWithMemberCountByUserId(@Param("userId") Long userId);


        // 그룹 검색 (엔터티 Page)
        @Query(value = "select g from ApplicationGroup g " +
                        "where (:q is null or :q = '' or lower(g.name) like lower(concat('%', :q, '%')))", countQuery = "select count(g) from ApplicationGroup g "
                        + "where (:q is null or :q = '' or lower(g.name) like lower(concat('%', :q, '%')))")
        Page<ApplicationGroup> findGroupsByName(@Param("q") String q, Pageable pageable);

        @Query(value = "select g as entity, count(m) as memberCount " +
                        "from ApplicationGroup g " +
                        "left join g.memberships m " + // ← 컬렉션명 확인(필요시 변경)
                        "where (:q is null or :q = '' or lower(g.name) like lower(concat('%', :q, '%'))) " +
                        "group by g", countQuery = "select count(distinct g) " +
                                        "from ApplicationGroup g " +
                                        "where (:q is null or :q = '' or lower(g.name) like lower(concat('%', :q, '%')))")
        Page<ApplicationGroupWithMemberCount> findGroupsWithMemberCountByName(@Param("q") String q, Pageable pageable);

}