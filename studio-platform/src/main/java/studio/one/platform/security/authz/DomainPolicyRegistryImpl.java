package studio.one.platform.security.authz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link DomainPolicyRegistry} that only merges policies
 * provided by {@link DomainPolicyContributor} beans (e.g., database-backed
 * ACLs).
 */
@Slf4j
public class DomainPolicyRegistryImpl implements DomainPolicyRegistry {

    private final ObjectProvider<java.util.List<DomainPolicyContributor>> contributors;

    private volatile Map<String, AclProperties.DomainPolicy> merged = Collections.emptyMap();

    /**
     * Creates a strategy that merges policies coming from contributors only.
     *
     * @param contributors provider for the list of contributors
     */
    public DomainPolicyRegistryImpl(ObjectProvider<java.util.List<DomainPolicyContributor>> contributors) {
        this.contributors = contributors;
        refresh();
    }

    @EventListener
    public void refresh(DomainPolicyRefreshEvent event) {
        refresh();
    }

    private synchronized void refresh() {
        Map<String, AclProperties.DomainPolicy> next = new HashMap<>();
        var list = Optional.ofNullable(contributors)
                .map(ObjectProvider::getIfAvailable)
                .orElse(Collections.emptyList());
        for (DomainPolicyContributor c : list) {
            Map<String, AclProperties.DomainPolicy> m = mSafe(c.contribute());
            m.forEach((k, v) -> next.merge(norm(k), freezeDomain(copyDomain(v)), this::deepMerge));
        }

        merged = Collections.unmodifiableMap(next);
        if (log.isDebugEnabled()) {
            log.debug("Domain policies merged: {}", merged.keySet());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> requiredRoles(String resource, String action) {
        Map<String, AclProperties.DomainPolicy> snapshot = merged;
        Parsed p = parse(resource);
        var dp = Optional.ofNullable(snapshot.get(norm(p.domain))).orElse(null);
        if (dp == null)
            return Collections.emptyList();

        AclProperties.ComponentPolicy cp = null;
        if (p.component != null && dp.getComponents() != null) {
            cp = dp.getComponents().get(p.component);
        }
        AclProperties.Roles roles = (cp != null && cp.getRoles() != null) ? cp.getRoles() : dp.getRoles();
        if (roles == null)
            return Collections.emptyList();

        switch (normalizeAction(action)) {
            case "read":
                return lSafe(roles.getRead());
            case "write":
                return lSafe(roles.getWrite());
            case "admin":
                List<String> admin = roles.getAdmin();
                return (admin != null) ? admin : lSafe(roles.getWrite()); // admin → write
            default:
                return Collections.emptyList();
        }
    }

    // ===== 병합/정규화 =====

    private AclProperties.DomainPolicy deepMerge(AclProperties.DomainPolicy a, AclProperties.DomainPolicy b) {
        if (b.getRoles() != null)
            a.setRoles(mergeRoles(a.getRoles(), b.getRoles()));
        if (b.getComponents() != null) {
            if (a.getComponents() == null)
                a.setComponents(new HashMap<>());
            b.getComponents().forEach((ck, cv) -> {
                String nck = norm(ck);
                var existing = a.getComponents().get(nck);
                if (existing == null)
                    a.getComponents().put(nck, freezeComponent(copyComponent(cv)));
                else if (cv.getRoles() != null)
                    existing.setRoles(mergeRoles(existing.getRoles(), cv.getRoles()));
            });
        }
        return freezeDomain(a);
    }

    private AclProperties.DomainPolicy copyDomain(AclProperties.DomainPolicy src) {
        AclProperties.DomainPolicy d = new AclProperties.DomainPolicy();
        d.setRoles(copyRoles(src.getRoles()));
        if (src.getComponents() != null) {
            Map<String, AclProperties.ComponentPolicy> m = new HashMap<>();
            src.getComponents().forEach((k, v) -> m.put(norm(k), copyComponent(v)));
            d.setComponents(m);
        }
        return d;
    }

    private AclProperties.ComponentPolicy copyComponent(AclProperties.ComponentPolicy src) {
        AclProperties.ComponentPolicy c = new AclProperties.ComponentPolicy();
        c.setRoles(copyRoles(src.getRoles()));
        return c;
    }

    private AclProperties.Roles copyRoles(AclProperties.Roles src) {
        if (src == null)
            return null;
        AclProperties.Roles r = new AclProperties.Roles();
        r.setRead(copyList(src.getRead()));
        r.setWrite(copyList(src.getWrite()));
        r.setAdmin(copyList(src.getAdmin()));
        return r;
    }

    private AclProperties.Roles mergeRoles(AclProperties.Roles a, AclProperties.Roles b) {
        if (a == null)
            return freezeRoles(copyRoles(b));
        if (b == null)
            return freezeRoles(a);
        AclProperties.Roles r = new AclProperties.Roles();
        r.setRead(firstNonNull(b.getRead(), a.getRead()));
        r.setWrite(firstNonNull(b.getWrite(), a.getWrite()));
        r.setAdmin(firstNonNull(b.getAdmin(), a.getAdmin()));
        return freezeRoles(r);
    }

    private AclProperties.DomainPolicy freezeDomain(AclProperties.DomainPolicy d) {
        if (d == null)
            return null;
        d.setRoles(freezeRoles(d.getRoles()));
        if (d.getComponents() != null) {
            d.getComponents().replaceAll((k, v) -> freezeComponent(v));
            d.setComponents(Collections.unmodifiableMap(d.getComponents()));
        }
        return d;
    }

    private AclProperties.ComponentPolicy freezeComponent(AclProperties.ComponentPolicy c) {
        if (c == null)
            return null;
        c.setRoles(freezeRoles(c.getRoles()));
        return c;
    }

    private AclProperties.Roles freezeRoles(AclProperties.Roles r) {
        if (r == null)
            return null;
        r.setRead(freezeList(normalizeRoles(r.getRead())));
        r.setWrite(freezeList(normalizeRoles(r.getWrite())));
        r.setAdmin(freezeList(normalizeRoles(r.getAdmin())));
        return r;
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null)
            return null;
        return roles.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::stripRolePrefix)
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }

    private String stripRolePrefix(String s) {
        return (s != null && s.startsWith("ROLE_")) ? s.substring(5) : s;
    }

    // ===== 유틸 =====

    private static <T> List<T> copyList(List<T> v) {
        return v == null ? null : new ArrayList<>(v);
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    private static String norm(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, AclProperties.DomainPolicy> mSafe(Map<String, AclProperties.DomainPolicy> m) {
        return m == null ? Collections.emptyMap() : m;
    }

    private static <T> List<T> lSafe(List<T> v) {
        return v == null ? Collections.emptyList() : v;
    }

    private static <T> List<T> freezeList(List<T> v) {
        return v == null ? null : Collections.unmodifiableList(v);
    }

    private static class Parsed {
        final String domain;
        final String component;

        Parsed(String d, String c) {
            this.domain = d;
            this.component = c;
        }
    }

    private static Parsed parse(String resource) {
        if (resource == null)
            return new Parsed(null, null);
        String s = resource.trim();
        int i = s.indexOf(':');
        if (i < 0)
            return new Parsed(s, null);
        return new Parsed(s.substring(0, i), s.substring(i + 1));
    }

    private static String normalizeAction(String action) {
        if (action == null)
            return "read";
        switch (action.toLowerCase(Locale.ROOT)) {
            case "view":
            case "access":
            case "read":
                return "read";
            case "manage":
            case "write":
                return "write";
            case "admin":
                return "admin";
            default:
                return action.toLowerCase(Locale.ROOT);
        }
    }
}
