package studio.one.base.security.acl.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import studio.one.base.security.acl.application.policy.AclAction;
import studio.one.base.security.acl.application.policy.AclPolicyRefreshPublisher;
import studio.one.base.security.acl.application.policy.AclResourceLocation;
import studio.one.base.security.acl.application.policy.DatabaseAclDomainPolicyContributor;
import studio.one.base.security.acl.domain.port.AclClassRepository;
import studio.one.base.security.acl.domain.port.AclEntryRepository;
import studio.one.base.security.acl.domain.port.AclObjectIdentityRepository;
import studio.one.base.security.acl.domain.port.AclPolicyProjection;
import studio.one.base.security.acl.domain.port.AclSidRepository;
import studio.one.platform.security.acl.AclMetricsRecorder;
import studio.one.platform.security.authz.DomainPolicyContributor;
import studio.one.platform.security.authz.DomainPolicyRefreshEvent;
import studio.one.platform.security.authz.DomainPolicyRegistryImpl;
import studio.one.platform.service.DomainEvents;

class AclAdministrationServiceTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteObjectIdentityRefreshesEffectivePolicyAndKeepsOtherGrants() {
        PolicyHarness harness = new PolicyHarness(List.of(
                row(10L, 100L, "Document", "sales:document", "ROLE_VIEWER", AclAction.READ.getMask(), true),
                row(20L, 200L, "Document", "sales:other", "ROLE_EDITOR", AclAction.WRITE.getMask(), true)));

        assertThat(harness.registry.requiredRoles("sales:document", "read")).containsExactly("VIEWER");
        assertThat(harness.registry.requiredRoles("sales:other", "write")).containsExactly("EDITOR");

        harness.service.deleteObjectIdentity(100L);

        assertThat(harness.publisher.published()).isEqualTo(1);
        assertThat(harness.registry.requiredRoles("sales:document", "read")).isEmpty();
        assertThat(harness.registry.requiredRoles("sales:other", "write")).containsExactly("EDITOR");
    }

    @Test
    void deleteObjectIdentityDoesNotRefreshWhenDeleteFails() {
        PolicyHarness harness = new PolicyHarness(List.of(
                row(10L, 100L, "Document", "sales:document", "ROLE_VIEWER", AclAction.READ.getMask(), true)));
        harness.objectIdentities.failDelete.set(true);

        assertThatThrownBy(() -> harness.service.deleteObjectIdentity(100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delete failed");

        assertThat(harness.publisher.published()).isZero();
        assertThat(harness.registry.requiredRoles("sales:document", "read")).containsExactly("VIEWER");
    }

    @Test
    void deleteEntryUsesSameRefreshPathForEffectivePolicy() {
        PolicyHarness harness = new PolicyHarness(List.of(
                row(10L, 100L, "Document", "sales:document", "ROLE_VIEWER", AclAction.READ.getMask(), true),
                row(20L, 200L, "Document", "sales:other", "ROLE_EDITOR", AclAction.WRITE.getMask(), true)));

        harness.service.deleteEntry(10L);

        assertThat(harness.publisher.published()).isEqualTo(1);
        assertThat(harness.registry.requiredRoles("sales:document", "read")).isEmpty();
        assertThat(harness.registry.requiredRoles("sales:other", "write")).containsExactly("EDITOR");
    }

    @Test
    void deleteObjectIdentityRefreshIsNotVisibleBeforeTransactionCommit() {
        TransactionalPolicyHarness harness = new TransactionalPolicyHarness(List.of(
                row(10L, 100L, "Document", "sales:document", "ROLE_VIEWER", AclAction.READ.getMask(), true)));

        TransactionSynchronizationManager.initSynchronization();

        harness.service.deleteObjectIdentity(100L);

        assertThat(harness.domainEvents.afterCommitRegistrations()).isEqualTo(1);
        assertThat(harness.registry.requiredRoles("sales:document", "read")).containsExactly("VIEWER");

        TransactionSynchronizationUtils.triggerAfterCommit();

        assertThat(harness.registry.requiredRoles("sales:document", "read")).isEmpty();
    }

    private static PolicyRow row(
            Long entryId,
            Long objectIdentityId,
            String className,
            String objectIdentity,
            String sid,
            int mask,
            boolean granting) {
        return new PolicyRow(entryId, objectIdentityId, className, objectIdentity, sid, mask, granting);
    }

    private static AclAdministrationService service(
            AclObjectIdentityRepository objectIdentityRepository,
            AclEntryRepository entryRepository,
            AclPolicyRefreshPublisher refreshPublisher) {
        return new AclAdministrationService(
                repository(AclClassRepository.class),
                repository(AclSidRepository.class),
                objectIdentityRepository,
                entryRepository,
                refreshPublisher,
                AclMetricsRecorder.noop(),
                false);
    }

    private static DatabaseAclDomainPolicyContributor contributor(AclEntryRepository entryRepository) {
        return new DatabaseAclDomainPolicyContributor(
                entryRepository,
                (className, identity) -> {
                    if (identity == null) {
                        return null;
                    }
                    String[] parts = identity.split(":", 2);
                    String component = (parts.length > 1) ? parts[1] : null;
                    return new AclResourceLocation(parts[0], component);
                },
                mask -> {
                    for (AclAction action : AclAction.values()) {
                        if (action.getMask() == mask) {
                            return java.util.EnumSet.of(action);
                        }
                    }
                    return java.util.EnumSet.noneOf(AclAction.class);
                });
    }

    private static <T> T repository(Class<T> type) {
        InvocationHandler handler = (proxy, method, args) -> defaultReturn(method);
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }

    private static AclEntryRepository entryRepository(FakeAclStore store) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("findAllForPolicy".equals(method.getName())) {
                return store.findAllForPolicy();
            }
            if ("deleteById".equals(method.getName())) {
                store.deleteEntry((Long) args[0]);
                return null;
            }
            return defaultReturn(method);
        };
        return AclEntryRepository.class.cast(Proxy.newProxyInstance(
                AclEntryRepository.class.getClassLoader(),
                new Class<?>[] { AclEntryRepository.class },
                handler));
    }

    private static AclObjectIdentityRepository objectIdentityRepository(FakeAclStore store) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("deleteById".equals(method.getName())) {
                store.deleteObjectIdentity((Long) args[0]);
                return null;
            }
            return defaultReturn(method);
        };
        return AclObjectIdentityRepository.class.cast(Proxy.newProxyInstance(
                AclObjectIdentityRepository.class.getClassLoader(),
                new Class<?>[] { AclObjectIdentityRepository.class },
                handler));
    }

    private static Object defaultReturn(Method method) {
        if (method.getDeclaringClass() == Object.class) {
            if ("toString".equals(method.getName())) {
                return "proxy";
            }
            if ("hashCode".equals(method.getName())) {
                return 0;
            }
            if ("equals".equals(method.getName())) {
                return false;
            }
        }
        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class || returnType == long.class || returnType == short.class
                || returnType == byte.class) {
            return 0;
        }
        if (returnType == void.class) {
            return null;
        }
        if (List.class.isAssignableFrom(returnType)) {
            return List.of();
        }
        return null;
    }

    private static final class PolicyHarness {
        final FakeAclStore objectIdentities;
        final CountingPublisher publisher;
        final DomainPolicyRegistryImpl registry;
        final AclAdministrationService service;

        PolicyHarness(List<PolicyRow> rows) {
            FakeAclStore store = new FakeAclStore(rows);
            AclEntryRepository entryRepository = entryRepository(store);
            this.objectIdentities = store;
            this.publisher = new CountingPublisher();
            this.registry = new DomainPolicyRegistryImpl(provider(List.<DomainPolicyContributor>of(
                    contributor(entryRepository))));
            this.publisher.onRefresh(() -> registry.refresh(new DomainPolicyRefreshEvent()));
            this.service = service(
                    objectIdentityRepository(store),
                    entryRepository,
                    new AclPolicyRefreshPublisher(provider((DomainEvents) null), provider(publisher)));
        }
    }

    private static final class TransactionalPolicyHarness {
        final RecordingDomainEvents domainEvents;
        final DomainPolicyRegistryImpl registry;
        final AclAdministrationService service;

        TransactionalPolicyHarness(List<PolicyRow> rows) {
            FakeAclStore store = new FakeAclStore(rows);
            AclEntryRepository entryRepository = entryRepository(store);
            this.domainEvents = new RecordingDomainEvents();
            this.registry = new DomainPolicyRegistryImpl(provider(List.<DomainPolicyContributor>of(
                    contributor(entryRepository))));
            this.domainEvents.onRefresh(() -> registry.refresh(new DomainPolicyRefreshEvent()));
            this.service = service(
                    objectIdentityRepository(store),
                    entryRepository,
                    new AclPolicyRefreshPublisher(provider(domainEvents), provider((ApplicationEventPublisher) null)));
        }
    }

    private static final class FakeAclStore {
        private final List<PolicyRow> rows = new ArrayList<>();
        private final Set<Long> objectIdentities = new java.util.LinkedHashSet<>();
        private final AtomicBoolean failDelete = new AtomicBoolean();

        FakeAclStore(List<PolicyRow> rows) {
            this.rows.addAll(rows);
            rows.stream().map(PolicyRow::objectIdentityId).forEach(objectIdentities::add);
        }

        List<AclPolicyProjection> findAllForPolicy() {
            return rows.stream()
                    .filter(row -> objectIdentities.contains(row.objectIdentityId()))
                    .map(row -> (AclPolicyProjection) row)
                    .toList();
        }

        void deleteObjectIdentity(Long id) {
            if (failDelete.get()) {
                throw new IllegalStateException("delete failed");
            }
            objectIdentities.remove(id);
        }

        void deleteEntry(Long id) {
            rows.removeIf(row -> row.entryId().equals(id));
        }
    }

    private record PolicyRow(
            Long entryId,
            Long objectIdentityId,
            String className,
            String objectIdentity,
            String sid,
            int mask,
            boolean granting) implements AclPolicyProjection {

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public String getObjectIdentity() {
            return objectIdentity;
        }

        @Override
        public String getSid() {
            return sid;
        }

        @Override
        public boolean isPrincipal() {
            return false;
        }

        @Override
        public int getMask() {
            return mask;
        }

        @Override
        public boolean isGranting() {
            return granting;
        }
    }

    private static final class CountingPublisher implements ApplicationEventPublisher {
        private final AtomicInteger published = new AtomicInteger();
        private Runnable onRefresh = () -> {
        };

        @Override
        public void publishEvent(Object event) {
            if (event instanceof DomainPolicyRefreshEvent) {
                published.incrementAndGet();
                onRefresh.run();
            }
        }

        void onRefresh(Runnable onRefresh) {
            this.onRefresh = onRefresh;
        }

        int published() {
            return published.get();
        }
    }

    private static final class RecordingDomainEvents implements DomainEvents {
        private final AtomicInteger afterCommitRegistrations = new AtomicInteger();
        private Runnable onRefresh = () -> {
        };

        @Override
        public void publish(Object event) {
            if (event instanceof DomainPolicyRefreshEvent) {
                onRefresh.run();
            }
        }

        @Override
        public void publishAfterCommit(Object event) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                publish(event);
                return;
            }
            afterCommitRegistrations.incrementAndGet();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(event);
                }
            });
        }

        void onRefresh(Runnable onRefresh) {
            this.onRefresh = onRefresh;
        }

        int afterCommitRegistrations() {
            return afterCommitRegistrations.get();
        }
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new FixedObjectProvider<>(value);
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
            return (value != null) ? Stream.of(value) : Stream.empty();
        }

        @Override
        public Stream<T> orderedStream() {
            return stream();
        }
    }
}
