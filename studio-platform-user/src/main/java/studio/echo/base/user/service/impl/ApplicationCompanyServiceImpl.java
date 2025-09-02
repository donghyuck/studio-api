package studio.echo.base.user.service.impl;

import java.util.Objects;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.echo.base.user.domain.entity.ApplicationCompany;
import studio.echo.base.user.domain.repository.ApplicationCompanyRepository;
import studio.echo.base.user.service.ApplicationCompanyService;
import studio.echo.platform.exception.NotFoundException;

@Service(ApplicationCompanyService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional
public class ApplicationCompanyServiceImpl implements ApplicationCompanyService {

    private final ApplicationCompanyRepository companyRepo;

    @Override @Transactional(readOnly = true)
    public ApplicationCompany get(Long companyId) {
        return companyRepo.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company", companyId));
    }

    @Override
    public ApplicationCompany create(ApplicationCompany company) {
        company.setCompanyId(null);
        return companyRepo.save(company);
    }

    @Override
    public ApplicationCompany update(Long companyId, Consumer<ApplicationCompany> mutator) {
        ApplicationCompany c = get(companyId);
        mutator.accept(c);
        return companyRepo.save(c);
    }

    @Override
    public void delete(Long companyId) {
        companyRepo.deleteById(companyId);
    }

    @Override @Transactional(readOnly = true)
    public Page<ApplicationCompany> search(String q, Pageable pageable) {
        return companyRepo.search(q, pageable);
    }

    @Override
    public void setProperty(Long companyId, String name, String value) {
        ApplicationCompany c = get(companyId);
        Objects.requireNonNull(name, "property name");
        Objects.requireNonNull(value, "property value");
        c.getProperties().put(name, value);
        companyRepo.save(c);
    }

    @Override
    public void removeProperty(Long companyId, String name) {
        ApplicationCompany c = get(companyId);
        Objects.requireNonNull(name, "property name");
        c.getProperties().remove(name);
        companyRepo.save(c);
    }
}
