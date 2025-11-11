package studio.one.base.security.acl.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AclEntryDto {
    private Long id;
    private Long objectIdentityId;
    private String objectIdentity;
    private Long sidId;
    private String sid;
    private Integer aceOrder;
    private Integer mask;
    private boolean granting;
    private boolean auditSuccess;
    private boolean auditFailure;
}
