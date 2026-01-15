package studio.one.base.security.acl.policy;

import java.time.Duration;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.security.authz.acl.AclMetricsRecorder;

/**
 * Default implementation that delegates descriptor synchronization to the seeder.
 */
@RequiredArgsConstructor
@Slf4j
public class AclPolicySynchronizationServiceImpl implements AclPolicySynchronizationService {

    private final AclPolicySeeder seeder;
    private final AclPolicyRefreshPublisher refreshPublisher;
    private final AclMetricsRecorder metricsRecorder;
    private final boolean auditEnabled;

    @Override
    public void synchronize(AclPolicyDescriptor descriptor) {
        long started = System.nanoTime();
        seeder.apply(descriptor);
        refreshPublisher.publishAfterCommit();
        metricsRecorder.record("policy_sync", Duration.ofNanos(System.nanoTime() - started), 1);
        if (auditEnabled && log.isInfoEnabled()) {
            log.info("ACL_AUDIT action=policy_sync scope=single");
        }
    }

    @Override
    public void synchronizeAll(List<AclPolicyDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            return;
        }
        long started = System.nanoTime();
        descriptors.forEach(seeder::apply);
        refreshPublisher.publishAfterCommit();
        metricsRecorder.record("policy_sync", Duration.ofNanos(System.nanoTime() - started), descriptors.size());
        if (auditEnabled && log.isInfoEnabled()) {
            log.info("ACL_AUDIT action=policy_sync scope=bulk count={}", descriptors.size());
        }
    }
}
