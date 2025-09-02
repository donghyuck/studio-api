package studio.echo.base.security.web.controller;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.jwt.JwtTokenProvider;
import studio.echo.base.user.web.dto.LoginRequest;
import studio.echo.base.user.web.dto.LoginResponse;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("${" + PropertyKeys.Security.Jwt.Endpoints.BASE_PATH + ":/api/auth}")
@Slf4j
public class JwtAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        Cookie cookie = new Cookie("refresh_token", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");  
        cookie.setMaxAge(jwtTokenProvider.getMaxAgeForRefreshTtl()); 
        response.addCookie(cookie);
        return ResponseEntity.ok(ApiResponse.ok(new LoginResponse(accessToken)));
    }
    
}
