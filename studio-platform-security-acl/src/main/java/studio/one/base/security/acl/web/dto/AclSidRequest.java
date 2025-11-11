package studio.one.base.security.acl.web.dto;

import javax.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class AclSidRequest {
    private boolean principal;

    @NotBlank
    private String sid;
}
