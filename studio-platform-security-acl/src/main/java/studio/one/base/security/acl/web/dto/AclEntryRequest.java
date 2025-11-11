package studio.one.base.security.acl.web.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class AclEntryRequest {
    @NotNull
    private Long objectIdentityId;

    @NotNull
    private Long sidId;

    @NotNull
    @Min(0)
    private Integer mask;

    private Integer aceOrder;
    private boolean granting = true;
    private boolean auditSuccess;
    private boolean auditFailure;
}
