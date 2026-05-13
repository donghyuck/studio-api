package studio.one.base.user.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import studio.one.base.user.web.dto.response.MeProfileDto;
import studio.one.base.user.web.dto.request.MeProfilePatchRequest;
import studio.one.base.user.web.dto.request.MeProfilePutRequest;
import studio.one.base.user.web.dto.request.MePasswordChangeRequest;
import studio.one.platform.web.dto.ApiResponse;
import studio.one.base.user.web.dto.response.PasswordPolicyDto;

public interface UserMeApi {

    ResponseEntity<ApiResponse<MeProfileDto>> me(@AuthenticationPrincipal UserDetails principal);

    ResponseEntity<ApiResponse<MeProfileDto>> patchMe(@AuthenticationPrincipal UserDetails principal, MeProfilePatchRequest request);

    ResponseEntity<ApiResponse<MeProfileDto>> putMe(@AuthenticationPrincipal UserDetails principal, MeProfilePutRequest request);

    ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal UserDetails principal, MePasswordChangeRequest request);

    ResponseEntity<ApiResponse<PasswordPolicyDto>> passwordPolicy(@AuthenticationPrincipal UserDetails principal);
}
