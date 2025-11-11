package studio.one.base.security.acl.policy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.util.StringUtils;

/**
 * Default mapper that converts ACL class/identity pairs into normalized
 * domain/component keys.
 */
public class SimpleAclResourceMapper implements AclResourceMapper {

    private Map<String, String> domainAliases = new LinkedHashMap<>();
    private Set<String> domainIndicators = new LinkedHashSet<>(
            Set.of("*", "__domain__", "__root__"));

    public void setDomainAliases(Map<String, String> domainAliases) {
        this.domainAliases = (domainAliases == null) ? Collections.emptyMap() : new LinkedHashMap<>(domainAliases);
    }

    public void setDomainIndicators(Set<String> domainIndicators) {
        this.domainIndicators = (domainIndicators == null) ? Collections.emptySet() : new LinkedHashSet<>(domainIndicators);
    }

    @Override
    public AclResourceLocation map(String className, String identity) {
        String normalizedClass = sanitize(className);
        String normalizedIdentity = sanitize(identity);

        if (normalizedIdentity != null && normalizedIdentity.contains(":")) {
            int idx = normalizedIdentity.indexOf(':');
            String candidateDomain = trimToNull(normalizedIdentity.substring(0, idx));
            String candidateComponent = trimToNull(normalizedIdentity.substring(idx + 1));
            String domain = resolveDomain(candidateDomain != null ? candidateDomain : normalizedClass);
            String component = normalizeComponent(candidateComponent);
            if (domain == null)
                return null;
            return new AclResourceLocation(domain, component);
        }

        String domain = resolveDomain(normalizedClass);
        if (domain == null)
            return null;

        String component = null;
        if (normalizedIdentity != null && !domainIndicators.contains(normalizedIdentity)) {
            String candidate = normalizeComponent(normalizedIdentity);
            if (candidate != null && !candidate.equals(domain)) {
                component = candidate;
            }
        }
        return new AclResourceLocation(domain, component);
    }

    private String resolveDomain(String candidate) {
        String normalized = normalizeComponent(candidate);
        if (normalized == null)
            return null;
        return domainAliases.getOrDefault(normalized, normalized);
    }

    private String normalizeComponent(String value) {
        String sanitized = sanitize(value);
        if (sanitized == null)
            return null;
        if (domainIndicators.contains(sanitized))
            return null;
        return sanitized;
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value))
            return null;
        String trimmed = value.trim()
                .replace('\\', '/')
                .replaceAll("[\\s]+", "-")
                .replaceAll("[^A-Za-z0-9:_-]", "-")
                .replaceAll("-{2,}", "-")
                .toLowerCase(Locale.ROOT);
        trimmed = trimToNull(trimmed.replaceAll("^-+", "").replaceAll("-+$", ""));
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null)
            return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
