package studio.one.application.template.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.template.domain.model.Template;

public interface TemplatePersistenceRepository {

    Optional<Template> findById(long templateId);

    Optional<Template> findByIdAndCreatedBy(long templateId, long createdBy);

    Optional<Template> findByName(String name);

    Optional<Template> findByNameAndCreatedBy(String name, long createdBy);

    Template save(Template template);

    void deleteById(long templateId);

    Page<Template> page(Pageable pageable);

    Page<Template> pageByCreatedBy(long createdBy, Pageable pageable);

    Page<Template> page(Pageable pageable, String query, String fields);

    Page<Template> pageByCreatedBy(long createdBy, Pageable pageable, String query, String fields);
}
