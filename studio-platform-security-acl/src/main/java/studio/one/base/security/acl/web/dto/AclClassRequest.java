package studio.one.base.security.acl.web.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class AclClassRequest {
    @NotBlank
    private String className;
}
