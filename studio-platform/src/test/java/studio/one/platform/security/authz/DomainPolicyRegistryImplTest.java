package studio.one.platform.security.authz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import studio.one.platform.security.acl.AclProperties;

class DomainPolicyRegistryImplTest {

    @Test
    void mergesContributorPoliciesWithNormalizationAndComponentOverride() {
        AclProperties.DomainPolicy first = domain(
                listOf("ROLE_admin"),
                listOf("manager"),
                null,
                mapOf(" Dashboard ", component(listOf("viewer"), listOf("editor"), null)));
        AclProperties.DomainPolicy second = domain(
                null,
                null,
                null,
                mapOf("dashboard", component(listOf("auditor"), null, null)));

        DomainPolicyRegistryImpl registry = new DomainPolicyRegistryImpl(provider(
                contributor(mapOf(" Sales ", first)),
                contributor(mapOf("sales", second))));

        assertThat(registry.requiredRoles("sales", "read")).containsExactly("ADMIN");
        assertThat(registry.requiredRoles("sales", "write")).containsExactly("MANAGER");
        assertThat(registry.requiredRoles("sales:dashboard", "read")).containsExactly("AUDITOR");
        assertThat(registry.requiredRoles("sales:dashboard", "write")).containsExactly("EDITOR");
    }

    @Test
    void fallsBackAdminToWriteAndReturnsEmptyForUnknownResource() {
        DomainPolicyRegistryImpl registry = new DomainPolicyRegistryImpl(provider(
                contributor(mapOf("sales", domain(null, listOf("writer"), null, new HashMap<>())))));

        assertThat(registry.requiredRoles("sales", "admin")).containsExactly("WRITER");
        assertThat(registry.requiredRoles("unknown", "read")).isEmpty();
    }

    private static DomainPolicyContributor contributor(Map<String, AclProperties.DomainPolicy> policies) {
        return () -> policies;
    }

    @SafeVarargs
    private static ObjectProvider<List<DomainPolicyContributor>> provider(DomainPolicyContributor... contributors) {
        return new FixedObjectProvider<>(List.of(contributors));
    }

    private static AclProperties.DomainPolicy domain(
            List<String> read,
            List<String> write,
            List<String> admin,
            Map<String, AclProperties.ComponentPolicy> components) {
        AclProperties.DomainPolicy policy = new AclProperties.DomainPolicy();
        policy.setRoles(roles(read, write, admin));
        policy.setComponents(components);
        return policy;
    }

    private static AclProperties.ComponentPolicy component(List<String> read, List<String> write, List<String> admin) {
        AclProperties.ComponentPolicy policy = new AclProperties.ComponentPolicy();
        policy.setRoles(roles(read, write, admin));
        return policy;
    }

    private static AclProperties.Roles roles(List<String> read, List<String> write, List<String> admin) {
        AclProperties.Roles roles = new AclProperties.Roles();
        roles.setRead(read);
        roles.setWrite(write);
        roles.setAdmin(admin);
        return roles;
    }

    private static List<String> listOf(String... values) {
        return new ArrayList<>(List.of(values));
    }

    private static <K, V> Map<K, V> mapOf(K key, V value) {
        Map<K, V> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private static final class FixedObjectProvider<T> implements ObjectProvider<T> {

        private final T value;

        private FixedObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public Stream<T> stream() {
            return Stream.of(value);
        }

        @Override
        public Stream<T> orderedStream() {
            return stream();
        }
    }
}
