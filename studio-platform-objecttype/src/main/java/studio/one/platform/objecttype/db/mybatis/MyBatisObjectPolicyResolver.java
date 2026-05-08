package studio.one.platform.objecttype.db.mybatis;

import java.util.Optional;

import studio.one.platform.domain.model.TypeObject;
import studio.one.platform.objecttype.db.jdbc.model.JdbcObjectPolicy;
import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;

public class MyBatisObjectPolicyResolver implements ObjectPolicyResolver {

    private final ObjectTypeMyBatisStore store;

    public MyBatisObjectPolicyResolver(ObjectTypeMyBatisStore store) {
        this.store = store;
    }

    @Override
    public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        return store.findPolicy(metadata.getObjectType()).map(JdbcObjectPolicy::new);
    }

    @Override
    public Optional<ObjectPolicy> resolve(TypeObject object) {
        if (object == null) {
            return Optional.empty();
        }
        return store.findPolicy(object.getObjectType()).map(JdbcObjectPolicy::new);
    }
}
