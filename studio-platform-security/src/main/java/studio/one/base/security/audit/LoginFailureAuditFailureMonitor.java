/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file LoginFailureAuditFailureMonitor.java
 *      @date 2026
 *
 */

package studio.one.base.security.audit;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class LoginFailureAuditFailureMonitor {

    private final AtomicLong failureCount = new AtomicLong();

    private final AtomicLong rejectedExecutionCount = new AtomicLong();

    private final AtomicLong droppedExecutionCount = new AtomicLong();

    private volatile Instant lastFailureAt;

    private volatile String lastErrorType;

    private volatile Instant lastRejectedExecutionAt;

    private volatile Instant lastDroppedExecutionAt;

    public void record(Exception ex) {
        failureCount.incrementAndGet();
        lastFailureAt = Instant.now();
        lastErrorType = ex == null ? null : ex.getClass().getName();
    }

    public boolean shouldLogStackTrace() {
        long count = failureCount.get();
        return count == 1 || count % 1000 == 0;
    }

    public boolean shouldLogFailureSummary() {
        long count = failureCount.get();
        return count == 1 || count % 100 == 0;
    }

    public void recordRejectedExecution() {
        rejectedExecutionCount.incrementAndGet();
        lastRejectedExecutionAt = Instant.now();
    }

    public boolean shouldLogRejectedExecutionSummary() {
        long count = rejectedExecutionCount.get();
        return count == 1 || count % 100 == 0;
    }

    public void recordDroppedExecution() {
        droppedExecutionCount.incrementAndGet();
        lastDroppedExecutionAt = Instant.now();
    }

    public boolean shouldLogDroppedExecutionSummary() {
        long count = droppedExecutionCount.get();
        return count == 1 || count % 100 == 0;
    }

    public long getFailureCount() {
        return failureCount.get();
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public String getLastErrorType() {
        return lastErrorType;
    }

    public long getRejectedExecutionCount() {
        return rejectedExecutionCount.get();
    }

    public Instant getLastRejectedExecutionAt() {
        return lastRejectedExecutionAt;
    }

    public long getDroppedExecutionCount() {
        return droppedExecutionCount.get();
    }

    public Instant getLastDroppedExecutionAt() {
        return lastDroppedExecutionAt;
    }

}
