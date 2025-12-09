package studio.one.application.template.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.application.template.domain.entity.TemplateEntity;

public interface TemplateRepository extends JpaRepository<TemplateEntity, Long> {

    Optional<TemplateEntity> findByName(String name);
}
