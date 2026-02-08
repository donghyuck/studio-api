package studio.one.base.user.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import studio.one.base.user.web.dto.MeProfileDto;
import studio.one.base.user.web.dto.MeProfilePatchRequest;
import studio.one.base.user.web.dto.MeProfilePutRequest;
import studio.one.platform.web.dto.ApiResponse;

public interface UserMeControllerApi {

    ResponseEntity<ApiResponse<MeProfileDto>> me(UserDetails principal);

    ResponseEntity<ApiResponse<MeProfileDto>> patchMe(UserDetails principal, MeProfilePatchRequest request);

    ResponseEntity<ApiResponse<MeProfileDto>> putMe(UserDetails principal, MeProfilePutRequest request);
}
