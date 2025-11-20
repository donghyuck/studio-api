package studio.one.platform.security.event;

import java.io.Serializable;
import java.util.Objects;

/**
 * Event emitted when an {@code ApplicationRole} is created, updated, or deleted.
 */
public class RoleUpdatedEvent implements Serializable {

    public enum Action {
        CREATED,
        UPDATED,
        DELETED
    }

    private final Action action;
    private final String roleName;
    private final String previousRoleName;

    public RoleUpdatedEvent(Action action, String roleName, String previousRoleName) {
        this.action = Objects.requireNonNull(action, "action");
        this.roleName = (roleName == null) ? null : roleName.trim();
        this.previousRoleName = (previousRoleName == null) ? null : previousRoleName.trim();
    }

    public Action getAction() {
        return action;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getPreviousRoleName() {
        return previousRoleName;
    }

    public static RoleUpdatedEvent of(Action action, String roleName, String previousRoleName) {
        return new RoleUpdatedEvent(
                action,
                roleName,
                previousRoleName);
    }

}
