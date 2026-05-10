package studio.one.base.security.audit.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.security.audit.application.command.LoginFailQuery;
import studio.one.base.security.audit.domain.model.LoginFailureLog;
import studio.one.platform.constant.ServiceNames;

public interface LoginFailureQueryService {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":audit:login-failure-query-service";
    Page<LoginFailureLog> find(LoginFailQuery query, Pageable pageable);

}
