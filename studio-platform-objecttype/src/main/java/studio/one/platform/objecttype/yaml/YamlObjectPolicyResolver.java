package studio.one.platform.objecttype.yaml;

import java.util.Map;
import java.util.Optional;

import studio.one.platform.domain.model.TypeObject;
import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;

/**
 * YAML-backed policy resolver using in-memory maps.
 */
public class YamlObjectPolicyResolver implements ObjectPolicyResolver {

    private final Map<Integer, ObjectPolicy> byType;

    public YamlObjectPolicyResolver(Map<Integer, ObjectPolicy> byType) {
        this.byType = byType;
    }

    @Override
    public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byType.get(metadata.getObjectType()));
    }

    @Override
    public Optional<ObjectPolicy> resolve(TypeObject object) {
        if (object == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byType.get(object.getObjectType()));
    }
}
