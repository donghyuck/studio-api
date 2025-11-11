package studio.one.base.user.service.impl;

import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.persistence.ApplicationCompanyRepository;
import studio.one.base.user.service.ApplicationCompanyService;
import studio.one.platform.component.State;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Service(ApplicationCompanyService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ApplicationCompanyServiceImpl implements ApplicationCompanyService {

    private final ApplicationCompanyRepository companyRepo;

       private final ObjectProvider<I18n> i18nProvider;

    @PostConstruct
    void initialize() {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, "autoconfig.feature.service.details", "User",
                LogUtils.blue(getClass(), true), LogUtils.red(State.INITIALIZED.toString())));
    }

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
