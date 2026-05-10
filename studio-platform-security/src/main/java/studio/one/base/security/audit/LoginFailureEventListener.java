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
 *      @file LoginFailureEventListener.java
 *      @date 2025
 *
 */


package studio.one.base.security.audit;

import java.util.concurrent.Executor;

import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.transaction.support.TransactionOperations;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.audit.domain.model.LoginFailureLog;
import studio.one.base.security.audit.domain.port.LoginFailureLogRepository;

/**
 *
 * @author  donghyuck, son
 * @since 2025-09-25
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-25  donghyuck, son: 최초 생성.
 * </pre>
 */


@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class LoginFailureEventListener
    implements ApplicationListener<AuthenticationFailureBadCredentialsEvent>, Ordered {

  private final LoginFailureLogRepository logRepo;
  private final LoginFailureAuditFailureMonitor failureMonitor;
  private final Executor executor;
  private final TransactionOperations transactionOperations;

  public LoginFailureEventListener(LoginFailureLogRepository logRepo) {
    this(logRepo, new LoginFailureAuditFailureMonitor());
  }

  public LoginFailureEventListener(
      LoginFailureLogRepository logRepo,
      LoginFailureAuditFailureMonitor failureMonitor) {
    this(logRepo, failureMonitor, Runnable::run, null);
  }

  public LoginFailureEventListener(
      LoginFailureLogRepository logRepo,
      LoginFailureAuditFailureMonitor failureMonitor,
      Executor executor,
      TransactionOperations transactionOperations) {
    this.logRepo = logRepo;
    this.failureMonitor = failureMonitor;
    this.executor = executor == null ? Runnable::run : executor;
    this.transactionOperations = transactionOperations;
  }

  @Override
  public void onApplicationEvent(AuthenticationFailureBadCredentialsEvent event) {
    LoginFailureAuditEventData auditEvent = LoginFailureAuditEventDataExtractor.extract(event);
    try {
      executor.execute(() -> saveAuditLogWithTransaction(auditEvent));
    } catch (RuntimeException ex) {
      failureMonitor.recordRejectedExecution();
      failureMonitor.recordDroppedExecution();
    }
  }

  private void saveAuditLogWithTransaction(LoginFailureAuditEventData auditEvent) {
    if (transactionOperations == null) {
      saveAuditLog(auditEvent);
      return;
    }
    try {
      transactionOperations.executeWithoutResult(status -> saveAuditLog(auditEvent));
    } catch (Exception ex) {
      failureMonitor.record(ex);
      if (failureMonitor.shouldLogFailureSummary()) {
        log.warn("Login failure audit transaction failed. username={}, failures={}, reason={}",
            auditEvent.username(), failureMonitor.getFailureCount(), ex.toString());
      }
    }
  }

  private void saveAuditLog(LoginFailureAuditEventData auditEvent) {
    try {
      final LoginFailureLog entry = LoginFailureLog.builder()
          .username(auditEvent.username())
          .remoteIp(auditEvent.remoteIp())
          .userAgent(auditEvent.userAgent())
          .failureType(auditEvent.failureType())
          .message(auditEvent.message())
          .occurredAt(auditEvent.occurredAt())
          .build();

      logRepo.save(entry);
    } catch (Exception ex) {
      failureMonitor.record(ex);
      if (failureMonitor.shouldLogStackTrace()) {
        log.error("Login failure audit log write failed. username={}, failures={}, reason={}",
            auditEvent.username(), failureMonitor.getFailureCount(), ex.toString(), ex);
      } else if (failureMonitor.shouldLogFailureSummary()) {
        log.warn("Login failure audit log write failed. username={}, failures={}, reason={}",
            auditEvent.username(), failureMonitor.getFailureCount(), ex.toString());
      }
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
