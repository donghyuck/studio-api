package studio.one.platform.objecttype.router;

import studio.one.platform.domain.model.TypeObject;

/**
 * Evaluates permissions for a given object and action.
 *
 * @author donghyuck, son
 * @since 2026-01-22
 * @version 1.0
 */
public interface ObjectPermissionEvaluator {

    boolean isAllowed(TypeObject object, String action, Object context);

}
