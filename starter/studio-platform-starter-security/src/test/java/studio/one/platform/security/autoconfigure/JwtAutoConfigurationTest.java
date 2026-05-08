package studio.one.platform.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import studio.one.base.security.audit.ClientRequestDetails;
import studio.one.base.security.audit.ClientRequestDetailsAuthenticationDetailsSource;
import studio.one.base.security.jwt.JwtTokenProvider;
import studio.one.base.security.jwt.refresh.RefreshTokenStore;
import studio.one.base.security.web.controller.JwtAuthController;
import studio.one.base.user.web.dto.LoginRequest;
import studio.one.platform.service.I18n;
import studio.one.platform.constant.PropertyKeys;

class JwtAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    JwtAutoConfiguration.class))
            .withPropertyValues(
                    "studio.security.jwt.enabled=true",
                    "studio.security.jwt.jwt.enabled=false",
                    "studio.security.jwt.endpoints.login-enabled=true")
            .withBean(JwtTokenProvider.class, () -> mock(JwtTokenProvider.class))
            .withBean(UserDetailsService.class, () -> mock(UserDetailsService.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withBean(RefreshTokenStore.class, () -> mock(RefreshTokenStore.class))
            .withBean(I18n.class, () -> (code, args, locale) -> code);

    @Test
    void loginControllerUsesConfiguredCaptureIpHeader() {
        AtomicReference<Authentication> captured = new AtomicReference<>();
        AuthenticationManager authenticationManager = authenticationManagerCapturing(captured);

        contextRunner
                .withPropertyValues("studio.security.audit.login-failure.capture-ip-header=X-Forwarded-For")
                .withPropertyValues("studio.security.audit.login-failure.trusted-proxy-cidrs[0]=203.0.113.0/24")
                .withPropertyValues("studio.security.audit.login-failure.trusted-proxy-cidrs[1]=10.0.0.0/8")
                .withBean(AuthenticationManager.class, () -> authenticationManager)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtAuthController.class);

                    assertThrows(BadCredentialsException.class,
                            () -> context.getBean(JwtAuthController.class).login(loginRequest(), request(
                                    "::ffff:192.0.2.128, 10.0.0.1", "203.0.113.10"), mock(HttpServletResponse.class)));

                    ClientRequestDetails details = (ClientRequestDetails) captured.get().getDetails();
                    assertThat(details.getRemoteIp()).isEqualTo("192.0.2.128");
                    assertThat(details.getForwardedFor()).isEqualTo("::ffff:192.0.2.128, 10.0.0.1");
                });
    }

    @Test
    void loginControllerUsesRemoteAddressByDefault() {
        AtomicReference<Authentication> captured = new AtomicReference<>();
        AuthenticationManager authenticationManager = authenticationManagerCapturing(captured);

        contextRunner
                .withBean(AuthenticationManager.class, () -> authenticationManager)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtAuthController.class);

                    assertThrows(BadCredentialsException.class,
                            () -> context.getBean(JwtAuthController.class).login(loginRequest(), request(
                                    "198.51.100.99", "203.0.113.10"), mock(HttpServletResponse.class)));

                    ClientRequestDetails details = (ClientRequestDetails) captured.get().getDetails();
                    assertThat(details.getRemoteIp()).isEqualTo("203.0.113.10");
                    assertThat(details.getForwardedFor()).isNull();
                });
    }

    @Test
    void loginControllerUsesSharedAuthenticationDetailsSourceBean() {
        AtomicReference<Authentication> captured = new AtomicReference<>();
        AuthenticationManager authenticationManager = authenticationManagerCapturing(captured);
        ClientRequestDetailsAuthenticationDetailsSource detailsSource =
                new ClientRequestDetailsAuthenticationDetailsSource() {
                    @Override
                    public ClientRequestDetails buildDetails(HttpServletRequest context) {
                        return new ClientRequestDetails("10.10.10.10", "custom", "JUnit", null);
                    }
                };

        contextRunner
                .withBean(AuthenticationManager.class, () -> authenticationManager)
                .withBean(ClientRequestDetailsAuthenticationDetailsSource.class, () -> detailsSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtAuthController.class);

                    assertThrows(BadCredentialsException.class,
                            () -> context.getBean(JwtAuthController.class).login(loginRequest(), request(
                                    "198.51.100.99", "203.0.113.10"), mock(HttpServletResponse.class)));

                    ClientRequestDetails details = (ClientRequestDetails) captured.get().getDetails();
                    assertThat(details.getRemoteIp()).isEqualTo("10.10.10.10");
                    assertThat(details.getForwardedFor()).isEqualTo("custom");
                });
    }

    @Test
    void jwtEntityScanRegistrarUsesEntityPackagesPropertyKey() throws Exception {
        var registrar = JwtAutoConfiguration.EntityScanConfig.jwtEntityScanRegistrar(null, i18n());
        Field key = registrar.getClass().getDeclaredField("key");
        key.setAccessible(true);

        assertThat(key.get(registrar))
                .isEqualTo(PropertyKeys.Security.Jwt.PREFIX + ".entity-packages");
    }

    private AuthenticationManager authenticationManagerCapturing(AtomicReference<Authentication> captured) {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        when(authenticationManager.authenticate(any(Authentication.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            throw new BadCredentialsException("bad credentials");
        });
        return authenticationManager;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("kim.owner");
        request.setPassword("wrong");
        return request;
    }

    private I18n i18n() {
        return (code, args, locale) -> code;
    }

    private HttpServletRequest request(String forwardedFor, String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        return request;
    }
}
