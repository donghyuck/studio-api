package studio.one.application.template.persistence.jpa.repo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import studio.one.application.template.domain.model.Template;
import studio.one.application.template.persistence.TemplatePersistenceRepository;
import studio.one.application.template.persistence.jpa.entity.TemplateEntity;

public class TemplateJpaPersistenceRepository implements TemplatePersistenceRepository {

    private final TemplateJpaRepository repository;

    public TemplateJpaPersistenceRepository(TemplateJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Template> findById(long templateId) {
        return repository.findById(templateId).map(t -> (Template) t);
    }

    @Override
    public Optional<Template> findByName(String name) {
        return repository.findByName(name).map(t -> (Template) t);
    }

    @Override
    public Template save(Template template) {
        TemplateEntity entity = toEntity(template);
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }

    @Override
    public void deleteById(long templateId) {
        repository.deleteById(templateId);
    }

    @Override
    public Page<Template> page(Pageable pageable) {
        return repository.findAll(pageable).map(t -> (Template) t);
    }

    @Override
    public Page<Template> page(Pageable pageable, String query, String fields) {
        if (!StringUtils.hasText(query)) {
            return page(pageable);
        }
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        Specification<TemplateEntity> spec = buildSearchSpec(needle, resolveFields(fields));
        return repository.findAll(spec, pageable).map(t -> (Template) t);
    }

    private TemplateEntity toEntity(Template template) {
        if (template instanceof TemplateEntity entity) {
            return entity;
        }
        TemplateEntity entity = new TemplateEntity();
        entity.setTemplateId(template.getTemplateId());
        entity.setObjectType(template.getObjectType());
        entity.setObjectId(template.getObjectId());
        entity.setName(template.getName());
        entity.setDisplayName(template.getDisplayName());
        entity.setDescription(template.getDescription());
        entity.setSubject(template.getSubject());
        entity.setBody(template.getBody());
        entity.setCreatedBy(template.getCreatedBy());
        entity.setUpdatedBy(template.getUpdatedBy());
        entity.setCreatedAt(template.getCreatedAt());
        entity.setUpdatedAt(template.getUpdatedAt());
        entity.setProperties(template.getProperties());
        return entity;
    }

    private Specification<TemplateEntity> buildSearchSpec(String needle, Set<String> fields) {
        return (root, query, cb) -> {
            String like = "%" + needle + "%";
            var predicates = new ArrayList<>();
            for (String field : fields) {
                var path = root.get(field).as(String.class);
                var lowered = cb.lower(cb.coalesce(path, ""));
                predicates.add(cb.like(lowered, like));
            }
            return cb.or(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        };
    }

    private Set<String> resolveFields(String fields) {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(
                "name", "displayName", "description", "subject", "body"));
        if (!StringUtils.hasText(fields)) {
            return allowed;
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String raw : fields.split(",")) {
            String field = raw.trim();
            if (allowed.contains(field)) {
                selected.add(field);
            }
        }
        return selected.isEmpty() ? allowed : selected;
    }
}
