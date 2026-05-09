package studio.one.platform.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

import studio.one.base.security.audit.ClientRequestDetailsAuthenticationDetailsSource;
import studio.one.base.security.audit.LoginFailureAuditFailureMonitor;
import studio.one.base.security.audit.persistence.jdbc.LoginFailureLogJdbcRepository;
import studio.one.base.security.audit.persistence.jpa.LoginFailureLogJpaRepository;
import studio.one.base.security.authentication.lock.service.AccountLockService;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;

class LoginFailureAuditAutoConfigurationTest {

    @Test
    void loginFailureAuditExecutorUsesBoundedQueueAndBackpressureMetrics() {
        LoginFailureAuditAutoConfiguration configuration = new LoginFailureAuditAutoConfiguration();
        AuditProperties properties = new AuditProperties();
        LoginFailureAuditFailureMonitor monitor = new LoginFailureAuditFailureMonitor();

        Executor executor = configuration.loginFailureAuditExecutor(properties, monitor, objectProvider(i18n()));

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        try {
            ThreadPoolExecutor threadPoolExecutor = taskExecutor.getThreadPoolExecutor();
            assertThat(threadPoolExecutor.getQueue().remainingCapacity()).isEqualTo(256);
            AtomicBoolean ranInCallerThread = new AtomicBoolean();
            threadPoolExecutor.getRejectedExecutionHandler().rejectedExecution(
                    () -> ranInCallerThread.set(true), threadPoolExecutor);
            assertThat(monitor.getRejectedExecutionCount()).isEqualTo(1);
            assertThat(ranInCallerThread.get()).isFalse();
        } finally {
            taskExecutor.shutdown();
        }
    }

    @Test
    void loginFailureAuditExecutorDropsAfterBoundedWaitWhenQueueStaysFull() {
        LoginFailureAuditAutoConfiguration configuration = new LoginFailureAuditAutoConfiguration();
        AuditProperties properties = new AuditProperties();
        LoginFailureAuditFailureMonitor monitor = new LoginFailureAuditFailureMonitor();
        Executor executor = configuration.loginFailureAuditExecutor(properties, monitor, objectProvider(i18n()));

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        try {
            ThreadPoolExecutor threadPoolExecutor = taskExecutor.getThreadPoolExecutor();
            while (threadPoolExecutor.getQueue().offer(() -> { })) {
                // Fill the queue so the rejection handler must take the bounded-drop path.
            }
            AtomicBoolean ranInCallerThread = new AtomicBoolean();
            RejectedExecutionHandler handler = threadPoolExecutor.getRejectedExecutionHandler();

            handler.rejectedExecution(() -> ranInCallerThread.set(true), threadPoolExecutor);

            assertThat(monitor.getRejectedExecutionCount()).isEqualTo(1);
            assertThat(monitor.getDroppedExecutionCount()).isEqualTo(1);
            assertThat(ranInCallerThread.get()).isFalse();
        } finally {
            taskExecutor.shutdown();
        }
    }

    @Test
    void loginFailureAuditExecutorProvidesNamedSynchronousExecutorWhenAsyncDisabled() {
        LoginFailureAuditAutoConfiguration configuration = new LoginFailureAuditAutoConfiguration();
        AuditProperties properties = new AuditProperties();
        properties.getLoginFailure().setAsync(false);

        Executor executor = configuration.loginFailureAuditExecutor(
                properties, new LoginFailureAuditFailureMonitor(), objectProvider(i18n()));

        assertThat(executor).isInstanceOf(SyncTaskExecutor.class);
    }

    @Test
    void loginFailureAuditListenerDoesNotUseGenericDataSourceGuard() {
        LoginFailureAuditAutoConfiguration configuration = new LoginFailureAuditAutoConfiguration();

        assertDoesNotThrow(() -> configuration.loginFailureEventListener(
                new LoginFailureLogJdbcRepository(mock(NamedParameterJdbcTemplate.class)),
                new LoginFailureAuditFailureMonitor(),
                Runnable::run,
                objectProvider(null),
                objectProvider(i18n())));

        assertDoesNotThrow(() -> configuration.loginFailureEventListener(
                mock(LoginFailureLogJpaRepository.class),
                new LoginFailureAuditFailureMonitor(),
                Runnable::run,
                objectProvider(null),
                objectProvider(i18n())));
    }

    @Test
    void loginFailureEntityScanRegistrarUsesEntityPackagesPropertyKey() throws Exception {
        var registrar = LoginFailureAuditAutoConfiguration.EntityScanConfig
                .auditEntityScanRegistrar(null, i18n());
        Field key = registrar.getClass().getDeclaredField("key");
        key.setAccessible(true);

        assertThat(key.get(registrar))
                .isEqualTo(PropertyKeys.Security.Audit.LOGIN_FAILURE + ".entity-packages");
    }

    @Test
    void loginFailureAuditConfigurationRegistersSeparateAccountLockListener() {
        LoginFailureAuditAutoConfiguration configuration = new LoginFailureAuditAutoConfiguration();
        assertThat(configuration.accountLockAuthenticationFailureListener(
                objectProvider(mock(AccountLockService.class)))).isNotNull();
    }

    @Test
    void clientRequestDetailsPostProcessorAppliesToAuthenticationProcessingFilters() throws Exception {
        ClientRequestDetailsAuthenticationDetailsSource detailsSource =
                new ClientRequestDetailsAuthenticationDetailsSource("X-Forwarded-For", List.of("203.0.113.0/24"));
        BeanPostProcessor postProcessor =
                LoginFailureAuditAutoConfiguration.clientRequestDetailsSecurityFilterChainPostProcessor(detailsSource);
        UsernamePasswordAuthenticationFilter filter = new UsernamePasswordAuthenticationFilter();
        SecurityFilterChain chain = new SecurityFilterChain() {
            @Override
            public boolean matches(HttpServletRequest request) {
                return true;
            }

            @Override
            public List<Filter> getFilters() {
                return List.of(filter);
            }
        };

        postProcessor.postProcessAfterInitialization(chain, "securityFilterChain");

        Field field = AbstractAuthenticationProcessingFilter.class.getDeclaredField("authenticationDetailsSource");
        field.setAccessible(true);
        assertThat(field.get(filter)).isSameAs(detailsSource);
    }

    @Test
    void clientRequestDetailsPostProcessorPreservesCustomAuthenticationDetailsSource() throws Exception {
        ClientRequestDetailsAuthenticationDetailsSource detailsSource =
                new ClientRequestDetailsAuthenticationDetailsSource("X-Forwarded-For", List.of("203.0.113.0/24"));
        BeanPostProcessor postProcessor =
                LoginFailureAuditAutoConfiguration.clientRequestDetailsSecurityFilterChainPostProcessor(detailsSource);
        UsernamePasswordAuthenticationFilter filter = new UsernamePasswordAuthenticationFilter();
        AuthenticationDetailsSource<jakarta.servlet.http.HttpServletRequest, String> customSource = request -> "custom";
        filter.setAuthenticationDetailsSource(customSource);
        SecurityFilterChain chain = new SecurityFilterChain() {
            @Override
            public boolean matches(HttpServletRequest request) {
                return true;
            }

            @Override
            public List<Filter> getFilters() {
                return List.of(filter);
            }
        };

        postProcessor.postProcessAfterInitialization(chain, "securityFilterChain");

        Field field = AbstractAuthenticationProcessingFilter.class.getDeclaredField("authenticationDetailsSource");
        field.setAccessible(true);
        assertThat(field.get(filter)).isSameAs(customSource);
    }

    @Test
    void clientRequestDetailsPostProcessorPreservesCustomWebAuthenticationDetailsSourceSubclass() throws Exception {
        ClientRequestDetailsAuthenticationDetailsSource detailsSource =
                new ClientRequestDetailsAuthenticationDetailsSource("X-Forwarded-For", List.of("203.0.113.0/24"));
        BeanPostProcessor postProcessor =
                LoginFailureAuditAutoConfiguration.clientRequestDetailsSecurityFilterChainPostProcessor(detailsSource);
        UsernamePasswordAuthenticationFilter filter = new UsernamePasswordAuthenticationFilter();
        WebAuthenticationDetailsSource customSource = new WebAuthenticationDetailsSource() {
        };
        filter.setAuthenticationDetailsSource(customSource);
        SecurityFilterChain chain = new SecurityFilterChain() {
            @Override
            public boolean matches(HttpServletRequest request) {
                return true;
            }

            @Override
            public List<Filter> getFilters() {
                return List.of(filter);
            }
        };

        postProcessor.postProcessAfterInitialization(chain, "securityFilterChain");

        Field field = AbstractAuthenticationProcessingFilter.class.getDeclaredField("authenticationDetailsSource");
        field.setAccessible(true);
        assertThat(field.get(filter)).isSameAs(customSource);
    }

    @Test
    void clientRequestDetailsPostProcessorIgnoresUnsupportedAuthenticationProcessingFilters() throws Exception {
        ClientRequestDetailsAuthenticationDetailsSource detailsSource =
                new ClientRequestDetailsAuthenticationDetailsSource("X-Forwarded-For", List.of("203.0.113.0/24"));
        BeanPostProcessor postProcessor =
                LoginFailureAuditAutoConfiguration.clientRequestDetailsSecurityFilterChainPostProcessor(detailsSource);
        AbstractAuthenticationProcessingFilter filter = new AbstractAuthenticationProcessingFilter(
                AnyRequestMatcher.INSTANCE) {
            @Override
            public Authentication attemptAuthentication(
                    HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response)
                    throws AuthenticationException {
                return null;
            }
        };
        SecurityFilterChain chain = new SecurityFilterChain() {
            @Override
            public boolean matches(HttpServletRequest request) {
                return true;
            }

            @Override
            public List<Filter> getFilters() {
                return List.of(filter);
            }
        };

        postProcessor.postProcessAfterInitialization(chain, "securityFilterChain");

        Field field = AbstractAuthenticationProcessingFilter.class.getDeclaredField("authenticationDetailsSource");
        field.setAccessible(true);
        assertThat(field.get(filter)).isInstanceOf(WebAuthenticationDetailsSource.class);
    }

    private I18n i18n() {
        return (code, args, locale) -> code;
    }

    private <T> ObjectProvider<T> objectProvider(T object) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return object;
            }

            @Override
            public T getObject() {
                return object;
            }

            @Override
            public T getIfAvailable() {
                return object;
            }

            @Override
            public void ifAvailable(java.util.function.Consumer<T> dependencyConsumer) {
                dependencyConsumer.accept(object);
            }

            @Override
            public Iterator<T> iterator() {
                return List.of(object).iterator();
            }
        };
    }
}
