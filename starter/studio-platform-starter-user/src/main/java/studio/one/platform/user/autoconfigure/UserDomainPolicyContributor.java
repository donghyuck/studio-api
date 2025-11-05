package studio.one.platform.user.autoconfigure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.CollectionUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.security.authz.AclProperties;
import studio.one.platform.security.authz.DomainPolicyContributor;

@RequiredArgsConstructor
@Slf4j
public class UserDomainPolicyContributor implements DomainPolicyContributor {

    private final WebProperties web;

    @Override
    public Map<String, AclProperties.DomainPolicy> contribute() {
        Map<String, AclProperties.DomainPolicy> out = new HashMap<>();
        if (web == null || web.getEndpoints() == null)
            return out;

        List<String> gRead = web.getRoles() != null ? web.getRoles().getRead() : null;
        List<String> gWrite = web.getRoles() != null ? web.getRoles().getWrite() : null;

        put(out, "user", web.getEndpoints().getUser(), gRead, gWrite);
        put(out, "group", web.getEndpoints().getGroup(), gRead, gWrite);
        put(out, "role", web.getEndpoints().getRole(), gRead, gWrite);
        put(out, "company", web.getEndpoints().getCompany(), gRead, gWrite);

        if (log.isDebugEnabled()) {
            log.debug("[ACL] legacy web policies → {}", out.keySet());
        }
        return out;
    }

    private void put(Map<String, AclProperties.DomainPolicy> out,
            String domainKey,
            WebProperties.Toggle ep,
            List<String> gRead, List<String> gWrite) {
        if (ep == null || !ep.isEnabled())
            return;

        WebProperties.Mode mode = ep.getMode() != null ? ep.getMode() : WebProperties.Mode.CRUD;

        List<String> eRead = (ep.getRoles() != null) ? ep.getRoles().getRead() : null;
        List<String> eWrite = (ep.getRoles() != null) ? ep.getRoles().getWrite() : null;

        List<String> read = firstNonEmpty(eRead, gRead);
        List<String> write = firstNonEmpty(eWrite, gWrite);

        switch (mode) {
            case DISABLED:
                return; // 정책 미생성 → 기본 거부
            case READ_ONLY:
                write = java.util.Collections.emptyList();
                break;
            case CRUD:
            default:
                break;
        }

        if (CollectionUtils.isEmpty(read) && CollectionUtils.isEmpty(write))
            return;

        AclProperties.Roles roles = new AclProperties.Roles();
        roles.setRead(emptyToNull(read));
        roles.setWrite(emptyToNull(write));
        roles.setAdmin(null);

        AclProperties.DomainPolicy dp = new AclProperties.DomainPolicy();
        dp.setRoles(roles);
        dp.setComponents(null);

        out.put(domainKey, dp);
    }

    private static <T> T firstNonEmpty(T a, T b) {
        if (a instanceof java.util.List) {
            java.util.List<?> al = (java.util.List<?>) a;
            if (al != null && !al.isEmpty())
                return a;
        } else if (a != null)
            return a;
        return b;
    }

    private static <T> T emptyToNull(T v) {
        if (v instanceof java.util.List) {
            java.util.List<?> l = (java.util.List<?>) v;
            return (T) (l == null || l.isEmpty() ? null : l);
        }
        return v;
    }
}
