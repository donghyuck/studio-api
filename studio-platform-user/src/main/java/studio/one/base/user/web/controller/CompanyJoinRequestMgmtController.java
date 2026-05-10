package studio.one.base.user.web.controller;

import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.domain.model.company.CompanyJoinRequestRef;
import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyMemberKeyRef;
import studio.one.base.user.domain.model.company.CompanyPermissionActions;
import studio.one.base.user.application.usecase.ApplicationCompanyJoinRequestService;
import studio.one.base.user.application.usecase.ApplicationCompanyPermissionService;
import studio.one.base.user.web.dto.response.CompanyJoinRequestDto;
import studio.one.base.user.web.dto.request.CompanyMemberKeyCreateRequest;
import studio.one.base.user.web.dto.response.CompanyMemberKeyDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.BASE_PATH + ":/api/mgmt}/companies")
@RequiredArgsConstructor
public class CompanyJoinRequestMgmtController implements CompanyJoinRequestMgmtApi {

    private final ApplicationCompanyPermissionService permissionService;
    private final ApplicationCompanyJoinRequestService joinRequestService;
    private final IdentityService identityService;
    private final ObjectProvider<Environment> environmentProvider;

    @PostMapping("/{companyId}/member-keys")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<CompanyMemberKeyDto>> createMemberKey(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyMemberKeyCreateRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        boolean platformAdmin = isPlatformAdmin();
        assertCompanyAction(companyId, principal, CompanyPermissionActions.MEMBER_MANAGE, platformAdmin);
        CompanyMemberKeyRef key = joinRequestService.createMemberKey(
                companyId,
                request.role(),
                request.expiresAt(),
                request.maxUses(),
                actorUserId(principal),
                platformAdmin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(toDto(key)));
    }

    @GetMapping("/{companyId}/member-join-requests")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<Page<CompanyJoinRequestDto>>> memberJoinRequests(
            @PathVariable Long companyId,
            @RequestParam(value = "status", required = false) CompanyJoinRequestStatus status,
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 15, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        assertCompanyAction(companyId, principal, CompanyPermissionActions.MEMBER_MANAGE);
        return ResponseEntity.ok(ApiResponse.ok(joinRequestService.getRequests(companyId, status, pageable).map(this::toDto)));
    }

    @PostMapping("/{companyId}/member-join-requests/{requestId}/approve")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<CompanyJoinRequestDto>> approveMemberJoinRequest(
            @PathVariable Long companyId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserDetails principal) {
        boolean platformAdmin = isPlatformAdmin();
        assertCompanyAction(companyId, principal, CompanyPermissionActions.MEMBER_MANAGE, platformAdmin);
        return ResponseEntity.ok(ApiResponse.ok(toDto(joinRequestService.approve(companyId, requestId, actorUserId(principal), platformAdmin))));
    }

    @PostMapping("/{companyId}/member-join-requests/{requestId}/reject")
    @PreAuthorize("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')")
    public ResponseEntity<ApiResponse<CompanyJoinRequestDto>> rejectMemberJoinRequest(
            @PathVariable Long companyId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserDetails principal) {
        assertCompanyAction(companyId, principal, CompanyPermissionActions.MEMBER_MANAGE);
        return ResponseEntity.ok(ApiResponse.ok(toDto(joinRequestService.reject(companyId, requestId, actorUserId(principal)))));
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

    private Long actorUserId(UserDetails principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return identityService.findByUsername(principal.getUsername())
                .map(studio.one.platform.identity.UserRef::userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No authenticated user"));
    }

    private CompanyMemberKeyDto toDto(CompanyMemberKeyRef key) {
        return new CompanyMemberKeyDto(
                key.keyId(),
                key.companyId(),
                key.role(),
                key.memberKey(),
                key.status(),
                key.expiresAt(),
                key.maxUses(),
                key.usedCount(),
                key.createdAt(),
                key.createdBy());
    }

    private CompanyJoinRequestDto toDto(CompanyJoinRequestRef request) {
        return new CompanyJoinRequestDto(
                request.requestId(),
                request.companyId(),
                request.keyId(),
                request.userId(),
                request.name(),
                request.email(),
                request.message(),
                request.requestedRole(),
                request.status(),
                request.requestedAt(),
                request.requestedBy(),
                request.decidedAt(),
                request.decidedBy());
    }
}
