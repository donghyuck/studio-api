package studio.one.base.user.web.controller;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.web.dto.response.CompanyJoinRequestDto;
import studio.one.base.user.web.dto.request.CompanyMemberKeyCreateRequest;
import studio.one.base.user.web.dto.response.CompanyMemberKeyDto;
import studio.one.platform.web.dto.ApiResponse;

public interface CompanyJoinRequestMgmtApi {

    ResponseEntity<ApiResponse<CompanyMemberKeyDto>> createMemberKey(
            Long companyId,
            @Valid CompanyMemberKeyCreateRequest request,
            UserDetails principal);

    ResponseEntity<ApiResponse<Page<CompanyJoinRequestDto>>> memberJoinRequests(
            Long companyId,
            CompanyJoinRequestStatus status,
            UserDetails principal,
            Pageable pageable);

    ResponseEntity<ApiResponse<CompanyJoinRequestDto>> approveMemberJoinRequest(
            Long companyId,
            Long requestId,
            UserDetails principal);

    ResponseEntity<ApiResponse<CompanyJoinRequestDto>> rejectMemberJoinRequest(
            Long companyId,
            Long requestId,
            UserDetails principal);
}
