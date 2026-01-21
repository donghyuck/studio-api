package studio.one.base.user.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import studio.one.base.user.web.dto.MeProfileDto;
import studio.one.platform.web.dto.ApiResponse;

public interface MeControllerApi {

    ResponseEntity<ApiResponse<MeProfileDto>> me(UserDetails principal);
}
