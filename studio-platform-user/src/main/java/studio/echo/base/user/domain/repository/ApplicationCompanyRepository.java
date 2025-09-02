package studio.echo.base.user.domain.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.echo.base.user.domain.entity.ApplicationCompany;

@Repository
public interface ApplicationCompanyRepository extends JpaRepository<ApplicationCompany, Long> {

    Optional<ApplicationCompany> findByName(String name);
    Optional<ApplicationCompany> findByDomainName(String domainName);
    boolean existsByName(String name);

    // 회사 검색 (엔터티 Page)
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
