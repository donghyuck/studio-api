package studio.one.base.user.persistence.jpa;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.persistence.ApplicationCompanyRepository;

@Repository(ApplicationCompanyRepository.SERVICE_NAME)
public interface ApplicationCompanyJpaRepository extends JpaRepository<ApplicationCompany, Long>, ApplicationCompanyRepository {

    @Override
    Optional<ApplicationCompany> findByName(String name);

    @Override
    Optional<ApplicationCompany> findByDomainName(String domainName);

    @Override
    boolean existsByName(String name);

    // 회사 검색 (엔터티 Page)
    @Override
    @Query(
      value = "select c from ApplicationCompany c " +
              "where (:q is null or :q = '' or " +
              "      lower(c.name) like lower(concat('%', :q, '%')) or " +
              "      lower(c.displayName) like lower(concat('%', :q, '%')) or " +
              "      lower(c.domainName) like lower(concat('%', :q, '%')))",
      countQuery = "select count(c) from ApplicationCompany c " +
                   "where (:q is null or :q = '' or " +
                   "      lower(c.name) like lower(concat('%', :q, '%')) or " +
                   "      lower(c.displayName) like lower(concat('%', :q, '%')) or " +
                   "      lower(c.domainName) like lower(concat('%', :q, '%')))"
    )
    Page<ApplicationCompany> search(@Param("q") String q, Pageable pageable);
}
