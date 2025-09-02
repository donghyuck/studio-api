package studio.echo.platform.security.authz;

import java.util.List;

public interface DomainPolicyRegistry {

    /**
     * @param resource "group" 또는 "user:profile" 형식의 리소스 키
     * @param action   read | write | admin
     * @return 요구 역할 목록(정규화됨, 예: ["ADMIN","MANAGER"]), 없으면 빈 리스트
     */
    List<String> requiredRoles(String resource, String action);
    
}