package studio.echo.base.security.jwt;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.RequiredArgsConstructor;
import studio.echo.base.security.exception.JwtTokenException;
import studio.echo.base.security.handler.AuthenticationErrorHandler;

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
}
