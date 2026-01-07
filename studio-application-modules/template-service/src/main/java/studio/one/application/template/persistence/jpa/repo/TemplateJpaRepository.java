package studio.one.application.template.persistence.jpa.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.application.template.persistence.jpa.entity.TemplateEntity;

public interface TemplateJpaRepository extends JpaRepository<TemplateEntity, Long> {

    Optional<TemplateEntity> findByName(String name);
}
