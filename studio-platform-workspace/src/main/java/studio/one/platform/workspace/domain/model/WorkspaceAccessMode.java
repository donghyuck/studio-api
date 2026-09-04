package studio.one.platform.workspace.domain.model;

/**
 * Controls whether a Team role is inherited by a Workspace.
 *
 * <p>Direct Workspace membership and parent Workspace role inheritance remain
 * independent from this mode. {@link #RESTRICTED} only prevents the owning
 * Team membership from granting implicit Workspace access.</p>
 */
public enum WorkspaceAccessMode {
    INHERIT,
    RESTRICTED
}
