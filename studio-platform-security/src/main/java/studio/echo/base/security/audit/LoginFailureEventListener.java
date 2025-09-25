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


package studio.echo.base.security.audit;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.authentication.AccountLockService;
import studio.echo.platform.constant.ServiceNames;

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


@RequiredArgsConstructor
@Slf4j
public class LoginFailureEventListener
    implements ApplicationListener<AuthenticationFailureBadCredentialsEvent> {

  private final ObjectProvider<AccountLockService> lockSvc;
  private final ObjectProvider<LoginFailureLogRepository> logRepo;

  @Async(ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EXECUTOR)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Override
  public void onApplicationEvent(AuthenticationFailureBadCredentialsEvent event) {
    final Instant now = Instant.now();

    String username = null;
    String remoteIp = null;
    String userAgent = null;

    try {
      if (event.getAuthentication() != null) {
        username = event.getAuthentication().getName();
        Object details = event.getAuthentication().getDetails();
        if (details instanceof ClientRequestDetails) {
          ClientRequestDetails crd = (ClientRequestDetails) details;
          remoteIp = crd.getRemoteIp();
          userAgent = crd.getUserAgent();
        } else if (details instanceof WebAuthenticationDetails) {
          remoteIp = ((WebAuthenticationDetails) details).getRemoteAddress();
        }
      }
    } catch (Exception ex) {
      log.debug("Failed to extract client details: {}", ex.toString(), ex);
    }

    // 람다에서 캡처할 값들을 final 로 확정
    final String uname = username;
    final String rip = remoteIp;
    final String ua = userAgent;
    final String fType = (event.getException() != null) ? event.getException().getClass().getSimpleName() : null;
    final String fMsg = (event.getException() != null) ? event.getException().getMessage() : null;
    final Instant occAt = now;

    // 1) 감사 로그 (엔트리를 먼저 만들고, 람다에는 entry만 넘김)
    try {
      final LoginFailureLog entry = LoginFailureLog.builder()
          .username(uname)
          .remoteIp(rip)
          .userAgent(ua)
          .failureType(fType)
          .message(fMsg)
          .occurredAt(occAt)
          .build();

      logRepo.ifAvailable(repo -> repo.save(entry));
    } catch (Exception ex) {
      log.warn("Login failure audit log write failed (ignored). username={}, reason={}", uname, ex.toString());
    }

    // 2) 계정 잠금 (final 값만 캡처)
    try {
      lockSvc.ifAvailable(s -> s.onFailedLogin(uname));
    } catch (Exception ex) {
      log.warn("Account lock update failed (ignored). username={}, reason={}", uname, ex.toString());
    }
  }

}
