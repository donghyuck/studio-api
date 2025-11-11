package studio.one.base.security.acl.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for ACL classes.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AclClassDto {

    private Long id;
    private String className;
}
