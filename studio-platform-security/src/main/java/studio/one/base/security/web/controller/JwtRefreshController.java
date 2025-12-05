/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file JwtRefreshController.java
 *      @date 2025
 *
 */

package studio.one.base.security.web.controller;

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
import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.base.security.jwt.refresh.RefreshTokenStore;
import studio.one.base.security.userdetails.UserIdToUsername;
import studio.one.base.security.web.dto.TokenRefreshResponse;
import studio.one.base.user.exception.UserNotFoundException;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.web.annotation.Message;
import studio.one.platform.web.dto.ApiResponse;

/**
 *
 * @author  donghyuck, son
 * @since 2025-12-05
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-12-05  donghyuck, son: 최초 생성.
 * </pre>
 */

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
