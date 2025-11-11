package studio.one.base.security.acl.policy;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper that writes ACL rows (class/sid/object/entry) into the legacy Spring
 * Security ACL tables so that domain policies can be assembled later.
 */
@Slf4j
@RequiredArgsConstructor
public class AclPolicySeeder {

    private static final Map<AclAction, Integer> ACTION_MASK = Map.of(
            AclAction.READ, org.springframework.security.acls.domain.BasePermission.READ.getMask(),
            AclAction.WRITE, org.springframework.security.acls.domain.BasePermission.WRITE.getMask(),
            AclAction.ADMIN, org.springframework.security.acls.domain.BasePermission.ADMINISTRATION.getMask());

    private final JdbcTemplate jdbcTemplate;

    public void apply(AclPolicyDescriptor descriptor) {
        if (descriptor == null || !StringUtils.hasText(descriptor.getDomain()))
            return;
        String domain = normalize(descriptor.getDomain());
        if (domain == null)
            return;
        String component = normalize(descriptor.getComponent());
        long classId = ensureClass(domain);
        String identity = buildIdentity(domain, component);
        long objectId = ensureObjectIdentity(classId, identity);
        for (AclPolicyDescriptor.AclRolePolicy role : descriptor.getRoles()) {
            if (role == null || !StringUtils.hasText(role.getRole()))
                continue;
            EnumSet<AclAction> actions = EnumSet.noneOf(AclAction.class);
            if (role.getActions() == null || role.getActions().isEmpty()) {
                actions.add(AclAction.READ);
            } else {
                actions.addAll(role.getActions());
            }
            int mask = maskFor(actions);
            if (mask == 0)
                continue;
            long sidId = ensureSid(role.getRole().trim().toUpperCase(Locale.ROOT));
            ensureEntry(objectId, sidId, mask);
            log.info("seeded ACL entry: domain={}, component={}, role={}, mask={}", domain, component, role.getRole(), mask);
        }
    }

    private long ensureClass(String domain) {
        jdbcTemplate.update("insert into acl_class (class) values (?) on conflict (class) do nothing", domain);
        return jdbcTemplate.queryForObject("select id from acl_class where class = ?", Long.class, domain);
    }

    private long ensureSid(String role) {
        jdbcTemplate.update("insert into acl_sid (principal, sid) values (false, ?) on conflict (sid, principal) do nothing", role);
        return jdbcTemplate.queryForObject("select id from acl_sid where sid = ? and principal = false", Long.class, role);
    }

    private long ensureObjectIdentity(long classId, String identity) {
        jdbcTemplate.update("""
                insert into acl_object_identity (object_id_class, object_id_identity, entries_inheriting)
                values (?, ?, true)
                on conflict (object_id_class, object_id_identity) do nothing
                """, classId, identity);
        return jdbcTemplate.queryForObject("""
                select id from acl_object_identity
                where object_id_class = ? and object_id_identity = ?
                """, Long.class, classId, identity);
    }

    private void ensureEntry(long objectId, long sidId, int mask) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1 from acl_entry
                    where acl_object_identity = ? and sid = ? and mask = ? and granting = true
                )
                """, Boolean.class, objectId, sidId, mask);
        if (Boolean.TRUE.equals(exists))
            return;
        Integer aceOrder = jdbcTemplate.queryForObject("""
                select coalesce(max(ace_order), -1) + 1 from acl_entry
                where acl_object_identity = ?
                """, Integer.class, objectId);
        jdbcTemplate.update("""
                insert into acl_entry (
                    acl_object_identity, ace_order, sid, mask, granting, audit_success, audit_failure
                ) values (?, ?, ?, ?, true, false, false)
                """, objectId, aceOrder, sidId, mask);
    }

    private int maskFor(EnumSet<AclAction> actions) {
        if (actions == null || actions.isEmpty())
            return 0;
        int mask = 0;
        for (AclAction action : actions) {
            Integer m = ACTION_MASK.get(action);
            if (m != null)
                mask |= m;
        }
        return mask;
    }

    private String buildIdentity(String domain, String component) {
        if (!StringUtils.hasText(component))
            return "__root__";
        return domain + ":" + component;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value))
            return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[\\s]+", "-")
                .replaceAll("[^a-z0-9:_-]", "-")
                .replaceAll("-{2,}", "-");
        trimmed = trimmed.replaceAll("^-+", "").replaceAll("-+$", "");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
