package studio.echo.base.user.service;

import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.echo.base.user.domain.entity.ApplicationCompany;

public interface ApplicationCompanyService {

    public static final String SERVICE_NAME = "components:application-company-service";

    ApplicationCompany get(Long companyId);

    ApplicationCompany create(ApplicationCompany company);

    ApplicationCompany update(Long companyId, Consumer<ApplicationCompany> mutator);

    void delete(Long companyId);

    // properties (ElementCollection or 별도 테이블)
    void setProperty(Long companyId, String name, String value);

    void removeProperty(Long companyId, String name);

    Page<ApplicationCompany> search(String q, Pageable pageable);
}
