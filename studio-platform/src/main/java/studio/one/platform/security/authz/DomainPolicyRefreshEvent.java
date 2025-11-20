package studio.one.platform.security.authz;

/**
 * Application event that signals domain policies should be reloaded from
 * contributors (e.g., after ACL sync).
 */
public class DomainPolicyRefreshEvent {

    public static DomainPolicyRefreshEvent of(){
        return new DomainPolicyRefreshEvent();
    }
}
