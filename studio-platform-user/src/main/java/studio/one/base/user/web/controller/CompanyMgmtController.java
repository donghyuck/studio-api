package studio.one.base.user.web.controller;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.model.company.CompanyMemberRef;
import studio.one.base.user.domain.model.company.CompanyPermissionPolicyRef;
import studio.one.base.user.domain.model.company.CompanyPermissionRolePolicyRef;
import studio.one.base.user.domain.model.company.CompanyPermissionActions;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.application.usecase.ApplicationCompanyMemberService;
import studio.one.base.user.application.usecase.ApplicationCompanyPermissionService;
import studio.one.base.user.application.usecase.ApplicationCompanyService;
import studio.one.base.user.web.dto.response.CompanyDto;
import studio.one.base.user.web.dto.response.CompanyMemberDto;
import studio.one.base.user.web.dto.request.CompanyMemberRequest;
import studio.one.base.user.web.dto.request.CompanyMemberRoleRequest;
import studio.one.base.user.web.dto.response.CompanyPermissionPolicyDto;
import studio.one.base.user.web.dto.request.CompanyPermissionPolicyUpdateRequest;
import studio.one.base.user.web.dto.response.CompanyPermissionRolePolicyDto;
import studio.one.base.user.web.dto.response.CompanyPermissionSummaryDto;
import studio.one.base.user.web.dto.request.CompanyUpdateRequest;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.BASE_PATH + ":/api/mgmt}/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyMgmtController {

    private final ApplicationCompanyService companyService;
    private final ApplicationCompanyMemberService memberService;
    private final ApplicationCompanyPermissionService permissionService;
    private final ObjectProvider<IdentityService> identityServiceProvider;
    private final ObjectProvider<Environment> environmentProvider;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:company','admin')")
    public ResponseEntity<ApiResponse<Page<CompanyDto>>> list(
            @RequestParam(value = "q", required = false) String q,
            @PageableDefault(size = 15, sort = "companyId", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.search(q, pageable).map(this::toDto)));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<CompanyDto>> create(
            @Valid @RequestBody CompanyDto request,
            @AuthenticationPrincipal UserDetails principal) {
        ApplicationCompany company = toEntity(request);
        ApplicationCompany saved = companyService.create(company, actorUserId(principal));
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(String.format("/companies/%s", saved.getCompanyId())));
        return new ResponseEntity<>(ApiResponse.ok(toDto(saved)), headers, HttpStatus.CREATED);
    }

    @GetMapping("/{companyId}")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')")
    public ResponseEntity<ApiResponse<CompanyDto>> get(
            @PathVariable Long companyId,
            @AuthenticationPrincipal UserDetails principal) {
        assertCompanyAction(companyId, principal, CompanyPermissionActions.READ);
        return ResponseEntity.ok(ApiResponse.ok(toDto(companyService.get(companyId))));
    }

    @PutMapping("/{companyId}")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<CompanyDto>> update(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyUpdateRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        assertCompanyAction(companyId, principal, CompanyPermissionActions.UPDATE);
        ApplicationCompany updated = companyService.update(companyId, company -> {
            company.setDisplayName(request.displayName());
            company.setDomainName(request.domainName());
            company.setDescription(request.description());
            company.setProperties(request.properties());
        });
        return ResponseEntity.ok(ApiResponse.ok(toDto(updated)));
    }

    @PostMapping("/{companyId}/archive")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<CompanyDto>> archive(
            @PathVariable Long companyId,
            @AuthenticationPrincipal UserDetails principal) {
        assertCompanyAction(companyId, principal, CompanyPermissionActions.ARCHIVE);
        return ResponseEntity.ok(ApiResponse.ok(toDto(companyService.archive(companyId, actorUserId(principal)))));
    }

    @GetMapping("/{companyId}/members")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')")
    public ResponseEntity<ApiResponse<Page<CompanyMemberDto>>> members(
            @PathVariable Long companyId,
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 15, sort = "userId", direction = Sort.Direction.ASC) Pageable pageable) {
        assertCompanyAction(companyId, principal, CompanyPermissionActions.MEMBER_READ);
        return ResponseEntity.ok(ApiResponse.ok(memberService.getMembers(companyId, pageable).map(this::toDto)));
    }

    @PostMapping("/{companyId}/members")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<CompanyMemberDto>> addMember(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyMemberRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        boolean platformAdmin = isPlatformAdmin();
        assertCompanyAction(companyId, principal, CompanyPermissionActions.MEMBER_MANAGE, platformAdmin);
        CompanyMemberRef ref = memberService.addMember(companyId, request.userId(), request.role(), actorUserId(principal), platformAdmin);
        return ResponseEntity.ok(ApiResponse.ok(toDto(ref)));
    }

    @PutMapping("/{companyId}/members/{userId}")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<CompanyMemberDto>> changeRole(
            @PathVariable Long companyId,
            @PathVariable Long userId,
            @Valid @RequestBody CompanyMemberRoleRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        boolean platformAdmin = isPlatformAdmin();
        assertCompanyAction(companyId, principal, CompanyPermissionActions.MEMBER_MANAGE, platformAdmin);
        CompanyMemberRef ref = memberService.changeRole(companyId, userId, request.role(), actorUserId(principal), platformAdmin);
        return ResponseEntity.ok(ApiResponse.ok(toDto(ref)));
    }

    @DeleteMapping("/{companyId}/members/{userId}")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long companyId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails principal) {
        boolean platformAdmin = isPlatformAdmin();
        assertCompanyAction(companyId, principal, CompanyPermissionActions.MEMBER_MANAGE, platformAdmin);
        memberService.removeMember(companyId, userId, actorUserId(principal), platformAdmin);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{companyId}/permissions/me")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')")
    public ResponseEntity<ApiResponse<CompanyPermissionSummaryDto>> permissionsMe(
            @PathVariable Long companyId,
            @AuthenticationPrincipal UserDetails principal) {
        assertCompanyAction(companyId, principal, CompanyPermissionActions.PERMISSION_READ);
        Long userId = actorUserId(principal);
        return ResponseEntity.ok(ApiResponse.ok(new CompanyPermissionSummaryDto(
                companyId,
                userId,
                permissionService.getGrantedActions(companyId, userId))));
    }

    @GetMapping("/{companyId}/permissions/actions")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')")
    public ResponseEntity<ApiResponse<List<String>>> permissionActions(
            @PathVariable Long companyId,
            @AuthenticationPrincipal UserDetails principal) {
        assertCompanyAction(companyId, principal, CompanyPermissionActions.PERMISSION_READ);
        companyService.get(companyId);
        return ResponseEntity.ok(ApiResponse.ok(studio.one.base.user.domain.model.company.CompanyPermissionActions.definitions()));
    }

    @GetMapping("/{companyId}/permissions/policy")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')")
    public ResponseEntity<ApiResponse<CompanyPermissionPolicyDto>> permissionPolicy(
            @PathVariable Long companyId,
            @AuthenticationPrincipal UserDetails principal) {
        assertCompanyAction(companyId, principal, CompanyPermissionActions.PERMISSION_READ);
        return ResponseEntity.ok(ApiResponse.ok(toDto(permissionService.getPolicy(companyId))));
    }

    @PutMapping("/{companyId}/permissions/policy")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<CompanyPermissionPolicyDto>> updatePermissionPolicy(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyPermissionPolicyUpdateRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        boolean platformAdmin = isPlatformAdmin();
        Long actorUserId = actorUserId(principal);
        try {
            return ResponseEntity.ok(ApiResponse.ok(toDto(permissionService.updatePolicy(
                    companyId,
                    request.roles().stream()
                            .map(role -> new CompanyPermissionRolePolicyRef(
                                    role.role(),
                                    role.actions(),
                                    List.of(),
                                    role.override()))
                            .toList(),
                    actorUserId,
                    platformAdmin))));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private void assertCompanyAction(Long companyId, UserDetails principal, String action) {
        assertCompanyAction(companyId, principal, action, isPlatformAdmin());
    }

    private void assertCompanyAction(Long companyId, UserDetails principal, String action, boolean platformAdmin) {
        if (platformAdmin) {
            return;
        }
        permissionService.assertGranted(companyId, actorUserId(principal), action);
    }

    private boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        String configuredAdminRole = Optional.ofNullable(environmentProvider.getIfAvailable())
                .map(environment -> environment.getProperty(PropertyKeys.Security.Acl.PREFIX + ".admin-role"))
                .filter(role -> !role.isBlank())
                .orElse("ROLE_ADMIN");
        return authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(configuredAdminRole::equals);
    }

    private ApplicationCompany toEntity(CompanyDto dto) {
        ApplicationCompany company = new ApplicationCompany();
        company.setName(dto.name());
        company.setDisplayName(dto.displayName());
        company.setDomainName(dto.domainName());
        company.setDescription(dto.description());
        company.setProperties(dto.properties());
        return company;
    }

    private CompanyDto toDto(ApplicationCompany company) {
        return new CompanyDto(
                company.getCompanyId(),
                company.getName(),
                company.getDisplayName(),
                company.getDomainName(),
                company.getDescription(),
                company.getStatus(),
                company.getArchivedAt(),
                company.getArchivedBy(),
                company.getCreationDate(),
                company.getModifiedDate(),
                company.getProperties());
    }

    private CompanyMemberDto toDto(CompanyMemberRef member) {
        return new CompanyMemberDto(
                member.companyId(),
                member.userId(),
                member.role(),
                member.status(),
                member.joinedAt(),
                member.joinedBy(),
                member.updatedAt(),
                member.updatedBy());
    }

    private CompanyPermissionPolicyDto toDto(CompanyPermissionPolicyRef policy) {
        return new CompanyPermissionPolicyDto(
                policy.companyId(),
                policy.roles().stream()
                        .map(role -> new CompanyPermissionRolePolicyDto(
                                role.role(),
                                role.actions(),
                                role.defaultActions(),
                                role.override()))
                        .toList());
    }

    private Long actorUserId(UserDetails principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        IdentityService identityService = identityServiceProvider.getIfAvailable();
        if (identityService == null) {
            throw new AuthenticationCredentialsNotFoundException("IdentityService is required to resolve company actor");
        }
        Optional<studio.one.platform.identity.UserRef> user = identityService.findByUsername(principal.getUsername());
        return user.map(studio.one.platform.identity.UserRef::userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No authenticated user"));
    }
}
