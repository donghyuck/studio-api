package studio.one.platform.objecttype.policy;

import java.util.Optional;

import studio.one.platform.domain.model.TypeObject;
import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;

/**
 * Resolves policies for object types or instances.
 *
 * @author donghyuck, son
 * @since 2026-01-22
 * @version 1.0
 */
public interface ObjectPolicyResolver {

    Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata);

    Optional<ObjectPolicy> resolve(TypeObject object);

}
