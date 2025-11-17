package studio.one.base.security.acl.web.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class AclEntryRequest {
    /**
     * objectIdentityId/sidId – 각각 ACL 객체 정체성과 SID(역할이나 사용자)를 가리키며 @NotNull로 필수
     * 입력입니다.
     */
    @NotNull
    private Long objectIdentityId;

    /**
     * objectIdentityId/sidId – 각각 ACL 객체 정체성과 SID(역할이나 사용자)를 가리키며 @NotNull로 필수
     * 입력입니다.
     */
    @NotNull
    private Long sidId;

    /**
     * mask – @Min(0)이므로 0 이상의 퍼미션 비트(예: 16=관리자, 1=매니저)를 전달합니다.
     */
    @NotNull
    @Min(0)
    private Integer mask;

    /**
     * aceOrder – ACL 내 엔트리 순서를 조절하는 선택적 값입니다.
     */
    private Integer aceOrder;
    /**
     * granting – 기본 true로 해당 마스크를 허용하는지 여부, false면 거부 형태로 작동합니다.
     */
    private boolean granting = true;
    /**
     * auditSuccess/auditFailure – 액세스 평가 성공/실패 시 감사 트리거 여부입니다.
     */
    private boolean auditSuccess;

    /**
     * auditSuccess/auditFailure – 액세스 평가 성공/실패 시 감사 트리거 여부입니다.
     */
    private boolean auditFailure;
}
