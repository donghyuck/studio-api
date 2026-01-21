package studio.one.platform.identity;

public interface PrincipalResolver {
    
    ApplicationPrincipal current(); // 인증 없으면 예외 또는 anonymous principal 반환

    ApplicationPrincipal currentOrNull(); // 인증 없으면 null
}
