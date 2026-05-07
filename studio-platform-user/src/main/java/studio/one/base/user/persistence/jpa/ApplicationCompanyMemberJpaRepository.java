package studio.one.base.user.persistence.jpa;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationCompanyMember;
import studio.one.base.user.domain.entity.ApplicationCompanyMemberId;
import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.persistence.ApplicationCompanyMemberRepository;

@Repository(ApplicationCompanyMemberRepository.SERVICE_NAME)
public interface ApplicationCompanyMemberJpaRepository
        extends JpaRepository<ApplicationCompanyMember, ApplicationCompanyMemberId>, ApplicationCompanyMemberRepository {

    @Override
    @Query("select m from ApplicationCompanyMember m where m.id.companyId = :companyId")
    Page<ApplicationCompanyMember> findAllByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    @Override
    @Query("select m from ApplicationCompanyMember m where m.id.companyId = :companyId order by m.id.userId")
    List<ApplicationCompanyMember> findAllByCompanyId(@Param("companyId") Long companyId);

    @Override
    @Query("select count(m) from ApplicationCompanyMember m where m.id.companyId = :companyId and m.role = :role")
    long countByCompanyIdAndRole(@Param("companyId") Long companyId, @Param("role") CompanyRole role);
}
