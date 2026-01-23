package studio.one.platform.objecttype.router;

import java.util.Optional;

import studio.one.platform.domain.model.TypeObject;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;

/**
 * Routes object types to permission evaluators.
 *
 * @author donghyuck, son
 * @since 2026-01-22
 * @version 1.0
 */
public interface AuthorizationRouter {

    Optional<ObjectPermissionEvaluator> route(ObjectTypeMetadata metadata);

    Optional<ObjectPermissionEvaluator> route(TypeObject object);

}
