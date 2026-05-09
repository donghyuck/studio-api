package studio.one.platform.objecttype.infrastructure.persistence.jpa.service;

import java.util.Optional;

import studio.one.platform.domain.model.TypeObject;
import studio.one.platform.objecttype.infrastructure.persistence.jpa.entity.ObjectTypePolicyEntity;
import studio.one.platform.objecttype.infrastructure.persistence.jpa.repo.ObjectTypePolicyJpaRepository;
import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;

public class JpaObjectPolicyResolver implements ObjectPolicyResolver {

    private final ObjectTypePolicyJpaRepository repository;

    public JpaObjectPolicyResolver(ObjectTypePolicyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        return repository.findById(metadata.getObjectType()).map(ObjectTypePolicyEntity.class::cast);
    }

    @Override
    public Optional<ObjectPolicy> resolve(TypeObject object) {
        if (object == null) {
            return Optional.empty();
        }
        return repository.findById(object.getObjectType()).map(ObjectTypePolicyEntity.class::cast);
    }
}
