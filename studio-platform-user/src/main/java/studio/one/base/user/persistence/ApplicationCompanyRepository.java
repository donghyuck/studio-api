package studio.one.base.user.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.entity.ApplicationCompany;

public interface ApplicationCompanyRepository {

    Page<ApplicationCompany> findAll(Pageable pageable);

    List<ApplicationCompany> findAll();

    Optional<ApplicationCompany> findById(Long companyId);

    Optional<ApplicationCompany> findByName(String name);

    Optional<ApplicationCompany> findByDomainName(String domainName);

    boolean existsByName(String name);

    Page<ApplicationCompany> search(String keyword, Pageable pageable);

    ApplicationCompany save(ApplicationCompany company);

    void delete(ApplicationCompany company);

    void deleteById(Long companyId);
}
