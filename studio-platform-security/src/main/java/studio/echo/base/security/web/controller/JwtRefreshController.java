package studio.echo.base.security.web.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.jwt.JwtTokenProvider;
import studio.echo.base.security.jwt.refresh.RefreshTokenStore;
import studio.echo.base.security.userdetails.UserIdToUsername;
import studio.echo.base.security.web.dto.TokenRefreshResponse;
import studio.echo.base.user.exception.UserNotFoundException;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.service.I18n;
import studio.echo.platform.web.annotation.Message;
import studio.echo.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("${" + PropertyKeys.Security.Jwt.Endpoints.BASE_PATH + ":/api/auth}")
@Slf4j
public class JwtRefreshController extends AbstractTokenController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final UserDetailsService userDetailsService;
    private final I18n i18n;

    @Value("${" + PropertyKeys.Security.Jwt.REFRESH_COOKIE_NAME + ":refresh_token}")
    private String refreshCookieName;

    @PostMapping("/refresh")
    @Message("success.token.refreshed")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        var cookie = WebUtils.getCookie(request, refreshCookieName);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh cookie missing");
        }
        
        String raw = cookie.getValue();
        Long userId = refreshTokenStore.resolveUserId(raw);
        String username = getUsernameByUserID(userId);
        UserDetails details = userDetailsService.loadUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(details, "", details.getAuthorities());
        String access = jwtTokenProvider.generateToken(authentication);
        String rotated = refreshTokenStore.rotate(raw); 
        addRefreshCookie(i18n, jwtTokenProvider, response, rotated);
        return ResponseEntity
                .ok(ApiResponse.<TokenRefreshResponse>ok(new TokenRefreshResponse(access)));

    }

    private String getUsernameByUserID(Long userId){
        if( userDetailsService instanceof UserIdToUsername byUserId )
        {
            return byUserId.usernameOf(userId);
        }
        throw new UserNotFoundException(userId);
    }

}
