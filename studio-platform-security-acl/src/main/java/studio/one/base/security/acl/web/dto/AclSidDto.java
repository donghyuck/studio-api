package studio.one.base.security.acl.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for ACL SIDs.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AclSidDto {
    private Long id;
    private boolean principal;
    private String sid;
}
