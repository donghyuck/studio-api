package studio.one.application.template.infrastructure.persistence.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import studio.one.application.template.infrastructure.persistence.jpa.TemplateEntity;

public interface TemplateJpaRepository extends JpaRepository<TemplateEntity, Long>,
        JpaSpecificationExecutor<TemplateEntity> {

    Optional<TemplateEntity> findByName(String name);

    Optional<TemplateEntity> findByTemplateIdAndCreatedBy(long templateId, long createdBy);

    Optional<TemplateEntity> findByNameAndCreatedBy(String name, long createdBy);
}
