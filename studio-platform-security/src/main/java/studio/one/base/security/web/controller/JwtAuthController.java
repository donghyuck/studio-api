package studio.one.base.security.web.controller;

import java.time.Instant;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.audit.ClientRequestDetails;
import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.base.security.jwt.refresh.RefreshTokenStore;
import studio.one.base.security.userdetails.ApplicationUserDetails;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.web.dto.LoginRequest;
import studio.one.base.user.web.dto.LoginResponse;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.web.annotation.Message;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("${" + PropertyKeys.Security.Jwt.Endpoints.BASE_PATH + ":/api/auth}")
@Slf4j
public class JwtAuthController  extends AbstractTokenController {

        private final AuthenticationManager authenticationManager;
        private final UserDetailsService userDetailsService;
        private final PasswordEncoder passwordEncoder;
        private final JwtTokenProvider jwtTokenProvider;
        private final ObjectProvider<RefreshTokenStore> store;
        private final I18n i18n;

        @PostMapping("/login")
        @Message("success.security.auth.login")
        public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                        HttpServletRequest http,
                        HttpServletResponse response) {

                var token = new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());
                token.setDetails(ClientRequestDetails.from(http));
                http.setAttribute("login.username", request.getUsername());
                
                UserDetails details = userDetailsService.loadUserByUsername(request.getUsername());

                log.debug("presentedPassword : {} , (P@sswOrd!), {}", token.getCredentials().toString(), StringUtils.equals("P@sswOrd!", token.getCredentials().toString()));
                log.debug("encoding: {}" , passwordEncoder.encode(token.getCredentials().toString()));
                log.debug("saved password : {}", details.getPassword());
                log.debug("password matches : {}", passwordEncoder.matches(token.getCredentials().toString(), details.getPassword()));

                Authentication authentication = authenticationManager.authenticate(token); 

                String accessToken = jwtTokenProvider.generateToken(authentication);
                String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
                 if( authentication.getPrincipal() instanceof ApplicationUserDetails && store.getIfAvailable() != null ){
                        ApplicationUserDetails<User> aud = (ApplicationUserDetails<User>) authentication.getPrincipal();
                        Long userId = aud.getUserId();
                        refreshToken = store.getObject().mint(userId);
                }
                addRefreshCookie(i18n, jwtTokenProvider, response, refreshToken );
                return ResponseEntity.ok(ApiResponse.ok(new LoginResponse(accessToken)));
        }
}
