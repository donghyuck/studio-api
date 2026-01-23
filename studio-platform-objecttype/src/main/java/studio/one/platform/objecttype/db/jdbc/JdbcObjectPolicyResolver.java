package studio.one.platform.objecttype.db.jdbc;

import java.util.Optional;

import studio.one.platform.domain.model.TypeObject;
import studio.one.platform.objecttype.db.jdbc.model.JdbcObjectPolicy;
import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;

public class JdbcObjectPolicyResolver implements ObjectPolicyResolver {

    private final ObjectTypeJdbcRepository repository;

    public JdbcObjectPolicyResolver(ObjectTypeJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        return repository.findPolicy(metadata.getObjectType()).map(JdbcObjectPolicy::new);
    }

    @Override
    public Optional<ObjectPolicy> resolve(TypeObject object) {
        if (object == null) {
            return Optional.empty();
        }
        return repository.findPolicy(object.getObjectType()).map(JdbcObjectPolicy::new);
    }
}
