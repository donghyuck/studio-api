package studio.one.base.user.domain.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.base.user.company.model.CompanyRole;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ApplicationCompanyPermissionPolicyId implements Serializable {

    @Column(name = "COMPANY_ID")
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", length = 30)
    private CompanyRole role;

    @Column(name = "ACTION_NAME", length = 100)
    private String action;
}
