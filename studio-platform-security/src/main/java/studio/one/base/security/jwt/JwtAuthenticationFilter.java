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
 *      @file JwtAuthenticationFilter.java
 *      @date 2025
 *
 */

package studio.one.base.security.jwt;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.RequiredArgsConstructor;
import studio.one.base.security.exception.JwtTokenException;
import studio.one.base.security.handler.AuthenticationErrorHandler;

/**
 * JWT 토큰 기반 인증을 처리하는 Spring Security 필터.
 * <p>
 * <ul>
 *   <li>HTTP 요청에서 JWT 토큰을 추출하고, 유효성을 검증합니다.</li>
 *   <li>유효한 토큰이 있으면 인증 정보를 SecurityContext에 저장합니다.</li>
 *   <li>토큰이 없거나 유효하지 않은 경우 {@link AuthenticationErrorHandler}를 통해 예외를 처리합니다.</li>
 *   <li>모든 요청에 대해 한 번만 실행되는 {@link OncePerRequestFilter}를 상속합니다.</li>
 * </ul>
 * </p>
 * 
 * @author  donghyuck, son
 * @since 2025-08-25
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-25  donghyuck, son: 최초 생성.
 * </pre>
 */


@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final String basePath;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final AuthenticationErrorHandler errorHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = jwtTokenProvider.resolveToken(request);
            if (token != null && jwtTokenProvider.validateToken(token)) {
                var auth = jwtTokenProvider.getAuthentication(token, userDetailsService, true);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            filterChain.doFilter(request, response);
        } catch (JwtTokenException ex) {
            errorHandler.handle(request, response, ex.getType(), ex.getArgs());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        if (path.startsWith( basePath )) {
            return false;
        }
        return super.shouldNotFilter(request);
    }
}
