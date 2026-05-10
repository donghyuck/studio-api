package studio.one.base.user.web.controller;

import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.domain.model.company.CompanyJoinRequestRef;
import studio.one.base.user.application.usecase.ApplicationCompanyJoinRequestService;
import studio.one.base.user.web.dto.response.CompanyJoinRequestDto;
import studio.one.base.user.web.dto.request.CompanySelfJoinRequestCreateRequest;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserRef;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
public class CompanySelfJoinRequestController implements CompanySelfJoinRequestApi {

    private final ApplicationCompanyJoinRequestService joinRequestService;
    private final IdentityService identityService;

    @PostMapping("${studio.features.user.web.self.path:/api/self}/company-join-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompanyJoinRequestDto>> createSelfJoinRequest(
            @Valid @RequestBody CompanySelfJoinRequestCreateRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        UserRef user = actor(principal);
        CompanyJoinRequestRef ref = joinRequestService.createSelfRequest(
                request.memberKey(),
                user.userId(),
                user.username(),
                null,
                request.message());
        return new ResponseEntity<>(ApiResponse.ok(toDto(ref)), HttpStatus.CREATED);
    }

    private UserRef actor(UserDetails principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        Optional<UserRef> user = identityService.findByUsername(principal.getUsername());
        return user.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No authenticated user"));
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
