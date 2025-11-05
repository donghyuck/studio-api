package studio.one.base.security.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import lombok.RequiredArgsConstructor;
import studio.one.base.security.authentication.AccountLockService;
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
public class LoginSuccessEventListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final ObjectProvider<AccountLockService> lockSvc;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent e) {
        String username = e.getAuthentication().getName();
        lockSvc.ifAvailable(s -> s.onSuccessfulLogin(username));
    }

}