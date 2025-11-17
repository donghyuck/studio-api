package studio.one.base.security.acl.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO that exposes available ACL actions along with their Spring Security mask
 * values so clients can present a permission selection list.
 */
@Getter
@AllArgsConstructor
public class AclActionMaskDto {

    private final String action;
    private final int mask;
}
