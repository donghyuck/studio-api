package studio.one.base.user.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.model.ApplicationCompanyPermissionPolicy;
import studio.one.base.user.domain.model.ApplicationCompanyPermissionPolicyId;
import studio.one.base.user.domain.port.ApplicationCompanyPermissionPolicyRepository;

@Repository(ApplicationCompanyPermissionPolicyRepository.SERVICE_NAME)
public interface ApplicationCompanyPermissionPolicyJpaRepository
        extends JpaRepository<ApplicationCompanyPermissionPolicy, ApplicationCompanyPermissionPolicyId>,
        ApplicationCompanyPermissionPolicyRepository {

    @Override
    @Query("""
            select p from ApplicationCompanyPermissionPolicy p
             where p.id.companyId = :companyId
             order by p.id.role asc, p.id.action asc
            """)
    List<ApplicationCompanyPermissionPolicy> findAllByCompanyId(@Param("companyId") Long companyId);

    @Override
    @Modifying
    @Query("delete from ApplicationCompanyPermissionPolicy p where p.id.companyId = :companyId")
    void deleteAllByCompanyId(@Param("companyId") Long companyId);
}
