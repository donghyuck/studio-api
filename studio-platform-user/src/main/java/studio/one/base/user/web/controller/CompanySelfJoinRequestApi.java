package studio.one.base.user.web.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import studio.one.base.user.web.dto.CompanyJoinRequestDto;
import studio.one.base.user.web.dto.CompanySelfJoinRequestCreateRequest;
import studio.one.platform.web.dto.ApiResponse;

public interface CompanySelfJoinRequestApi {

    ResponseEntity<ApiResponse<CompanyJoinRequestDto>> createSelfJoinRequest(
            @Valid CompanySelfJoinRequestCreateRequest request,
            UserDetails principal);
}
