package studio.one.base.security.acl.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import studio.one.base.security.acl.persistence.AclEntryRepository;
import studio.one.base.security.acl.persistence.AclPolicyProjection;

import studio.one.platform.security.authz.AclProperties;
import studio.one.platform.security.authz.DomainPolicyContributor;

/**
 * Builds domain policies from entries stored in the Spring Security ACL tables.
 */
@RequiredArgsConstructor
@Slf4j
public class DatabaseAclDomainPolicyContributor implements DomainPolicyContributor {

    private final AclEntryRepository repository;
    private final AclResourceMapper resourceMapper;
    private final AclPermissionMapper permissionMapper;

    @Override
    public Map<String, AclProperties.DomainPolicy> contribute() {
        List<AclPolicyProjection> rows = repository.findAllForPolicy();
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, MutableDomain> accumulator = new LinkedHashMap<>();
        for (AclPolicyProjection row : rows) {
            if (row == null || !row.isGranting()) {
                continue;
            }
            AclResourceLocation location = resourceMapper.map(row.getClassName(), row.getObjectIdentity());
            if (location == null || location.getDomain() == null) {
                continue;
            }
            String role = normalizeRole(row.getSid(), row.isPrincipal());
            if (role == null) {
                continue;
            }
            EnumSet<AclAction> actions = permissionMapper.map(row.getMask());
            if (actions.isEmpty()) {
                continue;
            }

            MutableDomain domain = accumulator.computeIfAbsent(location.getDomain(), k -> new MutableDomain());
            MutableRoles target = (location.getComponent() != null)
                    ? domain.component(location.getComponent())
                    : domain.root();
            target.apply(role, actions);
        }

        Map<String, AclProperties.DomainPolicy> result = new LinkedHashMap<>();
        accumulator.forEach((domainKey, mutableDomain) -> {
            AclProperties.DomainPolicy policy = mutableDomain.toDomainPolicy();
            if (policy != null) {
                result.put(domainKey, policy);
            }
        });

        if (log.isTraceEnabled()) {
            log.trace("[ACL] loaded {} policies from database:\n{}", result.size(), formatPolicies(result));
        } else if (log.isDebugEnabled()) {
            log.debug("[ACL] loaded {} policies from database", result.size());
        }
        return result;
    }

    private String normalizeRole(String sid, boolean principal) {
        if (principal) {
            return null; // skip user specific principals
        }
        if (sid == null) {
            return null;
        }
        String trimmed = sid.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("ROLE_")) {
            trimmed = trimmed.substring(5);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static final class MutableDomain {
        private final MutableRoles root = new MutableRoles();
        private final Map<String, MutableRoles> components = new LinkedHashMap<>();

        MutableRoles root() {
            return root;
        }

        MutableRoles component(String name) {
            Objects.requireNonNull(name, "component");
            return components.computeIfAbsent(name, k -> new MutableRoles());
        }

        AclProperties.DomainPolicy toDomainPolicy() {
            AclProperties.Roles rootRoles = root.toRoles();
            Map<String, AclProperties.ComponentPolicy> componentPolicies = new LinkedHashMap<>();
            components.forEach((key, value) -> {
                AclProperties.Roles roles = value.toRoles();
                if (roles != null) {
                    AclProperties.ComponentPolicy cp = new AclProperties.ComponentPolicy();
                    cp.setRoles(roles);
                    componentPolicies.put(key, cp);
                }
            });

            if (rootRoles == null && componentPolicies.isEmpty()) {
                return null;
            }
            AclProperties.DomainPolicy policy = new AclProperties.DomainPolicy();
            policy.setRoles(rootRoles);
            if (!componentPolicies.isEmpty()) {
                policy.setComponents(componentPolicies);
            }
            return policy;
        }
    }

    private static final class MutableRoles {
        private final Set<String> read = new LinkedHashSet<>();
        private final Set<String> write = new LinkedHashSet<>();
        private final Set<String> admin = new LinkedHashSet<>();

        void apply(String role, EnumSet<AclAction> actions) {
            if (actions.contains(AclAction.ADMIN)) {
                admin.add(role);
            }
            if (actions.contains(AclAction.WRITE)) {
                write.add(role);
            }
            if (actions.contains(AclAction.READ)) {
                read.add(role);
            }
        }

        AclProperties.Roles toRoles() {
            List<String> r = toList(read);
            List<String> w = toList(write);
            List<String> a = toList(admin);
            if (isEmpty(r) && isEmpty(w) && isEmpty(a)) {
                return null;
            }
            AclProperties.Roles roles = new AclProperties.Roles();
            roles.setRead(r);
            roles.setWrite(w);
            roles.setAdmin(a);
            return roles;
        }

        private List<String> toList(Set<String> values) {
            if (values.isEmpty()) {
                return null;
            }
            return new ArrayList<>(values);
        }

        private boolean isEmpty(List<String> values) {
            return values == null || values.isEmpty();
        }
    }

    private String formatPolicies(Map<String, AclProperties.DomainPolicy> policies) {
        return policies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + formatDomain(entry.getValue()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatDomain(AclProperties.DomainPolicy domainPolicy) {
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        String root = (domainPolicy.getRoles() != null) ? formatRoles(domainPolicy.getRoles()) : null;
        if (root != null) {
            joiner.add("root=" + root);
        }
        if (domainPolicy.getComponents() != null) {
            domainPolicy.getComponents().forEach((name, component) -> {
                String roles = (component != null) ? formatRoles(component.getRoles()) : null;
                if (roles != null) {
                    joiner.add(name + "=" + roles);
                }
            });
        }
        return joiner.toString();
    }

    private String formatRoles(AclProperties.Roles roles) {
        if (roles == null) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        String read = listToString(roles.getRead());
        String write = listToString(roles.getWrite());
        String admin = listToString(roles.getAdmin());
        if (read != null) {
            joiner.add("read=" + read);
        }
        if (write != null) {
            joiner.add("write=" + write);
        }
        if (admin != null) {
            joiner.add("admin=" + admin);
        }
        String result = joiner.toString();
        return (result.length() <= 2) ? null : result;
    }

    private String listToString(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().collect(Collectors.joining(", ", "[", "]"));
    }
}
