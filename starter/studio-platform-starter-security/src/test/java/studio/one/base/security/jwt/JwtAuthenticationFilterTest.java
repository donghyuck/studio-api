package studio.one.base.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import studio.one.base.security.handler.AuthenticationErrorHandler;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accountStatusFailureClearsSecurityContextAndReturnsUnauthorized() throws Exception {
        assertAuthenticationFailureClearsContext(new DisabledException("disabled"));
    }

    @Test
    void deletedUserFailureClearsSecurityContextAndReturnsUnauthorized() throws Exception {
        assertAuthenticationFailureClearsContext(new UsernameNotFoundException("deleted"));
    }

    @Test
    void downstreamFilterExceptionIsNotHandledAsAuthenticationFailure() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        AuthenticationErrorHandler errorHandler = mock(AuthenticationErrorHandler.class);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter("/api", tokenProvider, userDetailsService, errorHandler);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken("active", "n/a", "ROLE_USER");

        when(tokenProvider.resolveToken(request)).thenReturn("jwt");
        when(tokenProvider.validateToken("jwt")).thenReturn(true);
        when(tokenProvider.getAuthentication("jwt", userDetailsService, true)).thenReturn(authentication);

        assertThrows(ServletException.class, () -> filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    throw new ServletException("downstream");
                }));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
        verify(errorHandler, never()).unauthorized(request, response);
    }

    private void assertAuthenticationFailureClearsContext(RuntimeException failure) throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        AuthenticationErrorHandler errorHandler = mock(AuthenticationErrorHandler.class);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter("/api", tokenProvider, userDetailsService, errorHandler);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("stale", "n/a"));
        when(tokenProvider.resolveToken(request)).thenReturn("jwt");
        when(tokenProvider.validateToken("jwt")).thenReturn(true);
        when(tokenProvider.getAuthentication("jwt", userDetailsService, true))
                .thenThrow(failure);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.invoked).isFalse();
        verify(errorHandler).unauthorized(request, response);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/resource");
        request.addHeader(JwtTokenProvider.HEADER_STRING, "Bearer jwt");
        return request;
    }

    private static final class TrackingFilterChain implements FilterChain {
        private boolean invoked;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            invoked = true;
        }
    }
}
