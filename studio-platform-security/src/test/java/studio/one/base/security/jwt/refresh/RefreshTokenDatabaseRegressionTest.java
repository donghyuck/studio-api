package studio.one.base.security.jwt.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import studio.one.base.security.jwt.JwtConfig;
import studio.one.base.security.jwt.refresh.domain.model.RefreshToken;
import studio.one.base.security.jwt.refresh.domain.port.RefreshTokenRepository;
import studio.one.base.security.jwt.refresh.infrastructure.persistence.jdbc.RefreshTokenJdbcRepository;
import studio.one.base.security.jwt.refresh.infrastructure.persistence.jdbc.RefreshTokenJdbcRepositoryV2;
import studio.one.base.security.jwt.refresh.infrastructure.persistence.jpa.RefreshTokenJpaRepository;

@Testcontainers
class RefreshTokenDatabaseRegressionTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofMinutes(7);
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(30);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TestJwtConfig JWT_CONFIG = new TestJwtConfig();

    @Container
    static PostgreSQLContainer postgres = POSTGRES;

    private DriverManagerDataSource dataSource;
    private NamedParameterJdbcTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        template = new NamedParameterJdbcTemplate(dataSource);
        recreateSchema();
    }

    @Test
    void jdbcV1ConcurrentRotateCreatesOneSuccessor() throws Exception {
        try (StoreFixture fixture = jdbcFixture(RefreshTokenJdbcRepository::new)) {
            assertConcurrentRotateCreatesOneSuccessor(fixture);
        }
    }

    @Test
    void jdbcV2ConcurrentRotateCreatesOneSuccessor() throws Exception {
        try (StoreFixture fixture = jdbcFixture(RefreshTokenJdbcRepositoryV2::new)) {
            assertConcurrentRotateCreatesOneSuccessor(fixture);
        }
    }

    @Test
    void jpaConcurrentRotateCreatesOneSuccessor() throws Exception {
        try (StoreFixture fixture = jpaFixture()) {
            String predecessorRaw = assertConcurrentRotateCreatesOneSuccessor(fixture);

            TokenSnapshot predecessor = readTokenWithIndependentEntityManager(
                    fixture.entityManagerFactory(), selector(predecessorRaw));
            assertTrue(predecessor.revoked());
            assertNotNull(predecessor.replacedById());
        }
    }

    @Test
    void jpaInsertFailureRollsBackRevocation() {
        try (StoreFixture fixture = jpaFixture()) {
            String raw = fixture.store().mint(9001L, NOW.plus(Duration.ofHours(1)));
            installInsertFailureTrigger(9001L);

            assertThrows(RuntimeException.class, () -> fixture.store().rotate(raw));

            TokenSnapshot predecessor = readTokenWithIndependentEntityManager(
                    fixture.entityManagerFactory(), selector(raw));
            assertFalse(predecessor.revoked());
            assertNull(predecessor.replacedById());
            assertEquals(1, countTokensByUserId(9001L));
        }
    }

    @Test
    void jpaLinkFailureRollsBackSuccessor() {
        try (StoreFixture fixture = jpaFixture()) {
            String raw = fixture.store().mint(9002L, NOW.plus(Duration.ofHours(1)));
            installLinkFailureTrigger();

            assertThrows(RuntimeException.class, () -> fixture.store().rotate(raw));

            TokenSnapshot predecessor = readTokenWithIndependentEntityManager(
                    fixture.entityManagerFactory(), selector(raw));
            assertFalse(predecessor.revoked());
            assertNull(predecessor.replacedById());
            assertEquals(1, countTokensByUserId(9002L));
        }
    }

    @Test
    void jpaCommittedRevocationCannotBeRestoredByStaleState() {
        try (StoreFixture fixture = jpaFixture()) {
            String raw = fixture.store().mint(9003L, NOW.plus(Duration.ofHours(1)));
            AtomicReference<RefreshToken> stalePredecessor = new AtomicReference<>();
            AtomicBoolean staleDetachedAfterRevoke = new AtomicBoolean(false);
            AtomicBoolean staleFlushAttempted = new AtomicBoolean(false);
            fixture.repository().probeStaleState(selector(raw), predecessor -> {
                EntityManager transactionEntityManager = EntityManagerFactoryUtils
                        .getTransactionalEntityManager(fixture.entityManagerFactory());
                assertNotNull(transactionEntityManager);
                assertFalse(transactionEntityManager.contains(predecessor));
                staleDetachedAfterRevoke.set(true);
            }, predecessor -> {
                EntityManager transactionEntityManager = EntityManagerFactoryUtils
                        .getTransactionalEntityManager(fixture.entityManagerFactory());
                assertNotNull(transactionEntityManager);
                assertFalse(transactionEntityManager.contains(predecessor));

                stalePredecessor.set(predecessor);
                predecessor.setRevoked(false);
                predecessor.setReplacedBy(null);
                predecessor.setExpiresAt(predecessor.getExpiresAt().plus(Duration.ofMinutes(1)));
                transactionEntityManager.flush();
                staleFlushAttempted.set(true);
            });

            String successorRaw = fixture.store().rotate(raw);

            RefreshToken capturedPredecessor = stalePredecessor.get();
            assertNotNull(capturedPredecessor);
            assertFalse(capturedPredecessor.isRevoked());
            assertNull(capturedPredecessor.getReplacedBy());
            assertEquals(NOW.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(1)),
                    capturedPredecessor.getExpiresAt());
            assertTrue(staleDetachedAfterRevoke.get());
            assertTrue(staleFlushAttempted.get());

            TokenSnapshot committedPredecessor = readTokenWithIndependentEntityManager(
                    fixture.entityManagerFactory(), selector(raw));
            TokenSnapshot committedSuccessor = readTokenWithIndependentEntityManager(
                    fixture.entityManagerFactory(), selector(successorRaw));
            assertTrue(committedPredecessor.revoked());
            assertEquals(committedSuccessor.id(), committedPredecessor.replacedById());
            assertEquals(NOW.plus(Duration.ofHours(1)), committedPredecessor.expiresAt());
            assertNotEquals(capturedPredecessor.getExpiresAt(), committedPredecessor.expiresAt());
        }
    }

    private String assertConcurrentRotateCreatesOneSuccessor(StoreFixture fixture) throws Exception {
        String raw = fixture.store().mint(1001L, NOW.plus(Duration.ofHours(1)));
        String otherRaw = fixture.store().mint(2002L, NOW.plus(Duration.ofHours(1)));
        fixture.repository().armReadBarrier(selector(raw), 2);

        List<RotationResult> results = rotateConcurrently(fixture.store(), raw);

        assertEquals(2, fixture.repository().barrierReads());
        List<RotationResult> successes = results.stream().filter(RotationResult::success).toList();
        List<RotationResult> failures = results.stream().filter(result -> !result.success()).toList();
        assertEquals(1, successes.size());
        assertEquals(1, failures.size());
        assertInstanceOf(IllegalStateException.class, failures.get(0).failure());

        String successorRaw = successes.get(0).token();
        assertNotEquals(selector(raw), selector(successorRaw));

        TokenSnapshot predecessor = readToken(selector(raw));
        TokenSnapshot successor = readToken(selector(successorRaw));
        assertTrue(predecessor.revoked());
        assertEquals(successor.id(), predecessor.replacedById());
        assertEquals(1001L, successor.userId());
        assertFalse(successor.revoked());
        assertNull(successor.replacedById());
        assertEquals(NOW.plus(REFRESH_TTL), successor.expiresAt());
        assertEquals(2, countTokensByUserId(1001L));
        assertEquals(1, countActiveTokensByUserId(1001L));

        TokenSnapshot other = readToken(selector(otherRaw));
        assertEquals(2002L, other.userId());
        assertFalse(other.revoked());
        assertNull(other.replacedById());
        assertEquals(1, countTokensByUserId(2002L));

        assertEquals(1001L, fixture.store().resolveUserId(successorRaw));
        assertThrows(IllegalStateException.class, () -> fixture.store().rotate(raw));
        return raw;
    }

    private List<RotationResult> rotateConcurrently(RefreshTokenStore store, String raw) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<RotationResult> rotate = () -> {
                try {
                    return RotationResult.success(store.rotate(raw));
                } catch (Throwable failure) {
                    return RotationResult.failure(failure);
                }
            };
            Future<RotationResult> first = executor.submit(rotate);
            Future<RotationResult> second = executor.submit(rotate);
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private StoreFixture jdbcFixture(Function<NamedParameterJdbcTemplate, RefreshTokenRepository> repositoryFactory) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        AtomicReference<ControlledRefreshTokenRepository> controlledRepository = new AtomicReference<>();
        context.register(JdbcTxConfig.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(NamedParameterJdbcTemplate.class, () -> template);
        context.registerBean("transactionManager", PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(JwtConfig.class, () -> JWT_CONFIG);
        context.registerBean("refreshTokenRepositoryForStore", RefreshTokenRepository.class, () -> {
            ControlledRefreshTokenRepository controlled =
                    new ControlledRefreshTokenRepository(repositoryFactory.apply(template));
            controlledRepository.set(controlled);
            return controlled;
        });
        context.registerBean(RefreshTokenStore.class,
                () -> new HashedRefreshTokenStore(
                        context.getBean("refreshTokenRepositoryForStore", RefreshTokenRepository.class),
                        CLOCK,
                        context.getBean(JwtConfig.class)));
        context.refresh();
        return new StoreFixture(context, context.getBean(RefreshTokenStore.class), controlledRepository.get(), null);
    }

    private StoreFixture jpaFixture() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        AtomicReference<ControlledRefreshTokenRepository> controlledRepository = new AtomicReference<>();
        context.register(JpaTxConfig.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(NamedParameterJdbcTemplate.class, () -> template);
        context.registerBean(JwtConfig.class, () -> JWT_CONFIG);
        context.registerBean("refreshTokenRepositoryForStore", RefreshTokenRepository.class, () -> {
            ControlledRefreshTokenRepository controlled =
                    new ControlledRefreshTokenRepository(context.getBean(RefreshTokenJpaRepository.class));
            controlledRepository.set(controlled);
            return controlled;
        });
        context.registerBean(RefreshTokenStore.class,
                () -> new HashedRefreshTokenStore(
                        context.getBean("refreshTokenRepositoryForStore", RefreshTokenRepository.class),
                        CLOCK,
                        context.getBean(JwtConfig.class)));
        context.refresh();
        return new StoreFixture(
                context,
                context.getBean(RefreshTokenStore.class),
                controlledRepository.get(),
                context.getBean(EntityManagerFactory.class));
    }

    private TokenSnapshot readToken(String selector) {
        return template.queryForObject("""
                select ID, USER_ID, SELECTOR, EXPIRES_AT, REVOKED, REPLACED_BY_ID
                  from TB_APPLICATION_REFRESH_TOKEN
                 where SELECTOR = :selector
                """, Map.of("selector", selector), (rs, rowNum) -> new TokenSnapshot(
                rs.getLong("ID"),
                rs.getLong("USER_ID"),
                rs.getString("SELECTOR"),
                rs.getTimestamp("EXPIRES_AT").toInstant(),
                rs.getBoolean("REVOKED"),
                rs.getObject("REPLACED_BY_ID", Long.class)));
    }

    private TokenSnapshot readTokenWithIndependentEntityManager(
            EntityManagerFactory entityManagerFactory,
            String selector) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            RefreshToken token = entityManager
                    .createQuery("select token from RefreshToken token where token.selector = :selector",
                            RefreshToken.class)
                    .setParameter("selector", selector)
                    .getSingleResult();
            Long replacedById = token.getReplacedBy() == null ? null : token.getReplacedBy().getId();
            return new TokenSnapshot(
                    token.getId(),
                    token.getUserId(),
                    token.getSelector(),
                    token.getExpiresAt(),
                    token.isRevoked(),
                    replacedById);
        } finally {
            entityManager.close();
        }
    }

    private int countTokensByUserId(Long userId) {
        Integer count = template.queryForObject("""
                select count(*)
                  from TB_APPLICATION_REFRESH_TOKEN
                 where USER_ID = :userId
                """, Map.of("userId", userId), Integer.class);
        return count == null ? 0 : count;
    }

    private int countActiveTokensByUserId(Long userId) {
        Integer count = template.queryForObject("""
                select count(*)
                  from TB_APPLICATION_REFRESH_TOKEN
                 where USER_ID = :userId
                   and REVOKED = false
                """, Map.of("userId", userId), Integer.class);
        return count == null ? 0 : count;
    }

    private void installInsertFailureTrigger(Long userId) {
        template.getJdbcTemplate().execute("""
                create or replace function fail_refresh_insert_for_user()
                returns trigger
                language plpgsql
                as $$
                begin
                  if NEW.USER_ID = %d then
                    raise exception 'refresh insert failure';
                  end if;
                  return NEW;
                end
                $$
                """.formatted(userId));
        template.getJdbcTemplate().execute("""
                create trigger fail_refresh_insert_for_user
                before insert on TB_APPLICATION_REFRESH_TOKEN
                for each row execute function fail_refresh_insert_for_user()
                """);
    }

    private void installLinkFailureTrigger() {
        template.getJdbcTemplate().execute("""
                create or replace function fail_refresh_replacement_link()
                returns trigger
                language plpgsql
                as $$
                begin
                  if OLD.REPLACED_BY_ID is null and NEW.REPLACED_BY_ID is not null then
                    raise exception 'refresh link failure';
                  end if;
                  return NEW;
                end
                $$
                """);
        template.getJdbcTemplate().execute("""
                create trigger fail_refresh_replacement_link
                before update of REPLACED_BY_ID on TB_APPLICATION_REFRESH_TOKEN
                for each row execute function fail_refresh_replacement_link()
                """);
    }

    private void recreateSchema() throws Exception {
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_PASSWORD_RESET_TOKEN cascade");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_REFRESH_TOKEN cascade");
        template.getJdbcTemplate().execute("drop table if exists tb_login_failure_log cascade");
        template.getJdbcTemplate().execute("drop function if exists fail_refresh_insert_for_user() cascade");
        template.getJdbcTemplate().execute("drop function if exists fail_refresh_replacement_link() cascade");
        ClassPathResource resource = new ClassPathResource("schema/security/postgres/V400__create_security_tables.sql");
        String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String statement : splitPostgresStatements(script)) {
            if (!statement.isBlank()) {
                template.getJdbcTemplate().execute(statement);
            }
        }
    }

    private List<String> splitPostgresStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDollarQuote = false;
        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            if (ch == '$' && i + 1 < script.length() && script.charAt(i + 1) == '$') {
                inDollarQuote = !inDollarQuote;
                current.append("$$");
                i++;
                continue;
            }
            if (ch == ';' && !inDollarQuote) {
                String statement = current.toString().trim();
                if (!statement.isBlank()) {
                    statements.add(statement);
                }
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        String statement = current.toString().trim();
        if (!statement.isBlank()) {
            statements.add(statement);
        }
        return statements;
    }

    private static String selector(String raw) {
        return raw.substring(0, raw.indexOf('.'));
    }

    @Configuration
    @EnableTransactionManagement
    static class JdbcTxConfig {
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = RefreshTokenJpaRepository.class)
    static class JpaTxConfig {

        @org.springframework.context.annotation.Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(RefreshToken.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "none");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            factory.setJpaProperties(properties);
            return factory;
        }

        @org.springframework.context.annotation.Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }

    private static final class ControlledRefreshTokenRepository implements RefreshTokenRepository {

        private final RefreshTokenRepository delegate;
        private final AtomicBoolean barrierArmed = new AtomicBoolean(false);
        private final AtomicInteger barrierReads = new AtomicInteger();
        private final AtomicReference<RefreshToken> capturedStaleToken = new AtomicReference<>();
        private volatile String barrierSelector;
        private volatile CountDownLatch readBarrier;
        private volatile String staleProbeSelector;
        private volatile Consumer<RefreshToken> afterRevokeProbe;
        private volatile Consumer<RefreshToken> afterLinkProbe;

        private ControlledRefreshTokenRepository(RefreshTokenRepository delegate) {
            this.delegate = delegate;
        }

        void armReadBarrier(String selector, int expectedReads) {
            this.barrierSelector = selector;
            this.readBarrier = new CountDownLatch(expectedReads);
            this.barrierReads.set(0);
            this.barrierArmed.set(true);
        }

        int barrierReads() {
            return barrierReads.get();
        }

        void probeStaleState(
                String selector,
                Consumer<RefreshToken> afterRevokeProbe,
                Consumer<RefreshToken> afterLinkProbe) {
            this.staleProbeSelector = selector;
            this.afterRevokeProbe = afterRevokeProbe;
            this.afterLinkProbe = afterLinkProbe;
            this.capturedStaleToken.set(null);
        }

        @Override
        public RefreshToken save(RefreshToken token) {
            return delegate.save(token);
        }

        @Override
        public Optional<RefreshToken> findBySelector(String selector) {
            Optional<RefreshToken> token = delegate.findBySelector(selector);
            if (selector.equals(staleProbeSelector)) {
                token.ifPresent(capturedStaleToken::set);
            }
            CountDownLatch barrier = readBarrier;
            if (barrierArmed.get() && selector.equals(barrierSelector) && barrier != null) {
                int readNumber = barrierReads.incrementAndGet();
                barrier.countDown();
                try {
                    if (!barrier.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting for concurrent refresh reads");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("interrupted waiting for concurrent refresh reads", ex);
                }
                if (readNumber >= 2) {
                    barrierArmed.set(false);
                }
            }
            return token;
        }

        @Override
        public int revokeIfActive(Long id, String selector) {
            int revoked = delegate.revokeIfActive(id, selector);
            RefreshToken capturedToken = capturedStaleToken.get();
            Consumer<RefreshToken> probe = afterRevokeProbe;
            if (revoked == 1
                    && selector.equals(staleProbeSelector)
                    && capturedToken != null
                    && probe != null) {
                probe.accept(capturedToken);
            }
            return revoked;
        }

        @Override
        public int linkReplacement(Long predecessorId, String predecessorSelector, RefreshToken successor) {
            int linked = delegate.linkReplacement(predecessorId, predecessorSelector, successor);
            RefreshToken capturedToken = capturedStaleToken.get();
            Consumer<RefreshToken> probe = afterLinkProbe;
            if (linked == 1
                    && predecessorSelector.equals(staleProbeSelector)
                    && capturedToken != null
                    && probe != null) {
                probe.accept(capturedToken);
            }
            return linked;
        }
    }

    private record StoreFixture(
            AnnotationConfigApplicationContext context,
            RefreshTokenStore store,
            ControlledRefreshTokenRepository repository,
            EntityManagerFactory entityManagerFactory) implements AutoCloseable {

        @Override
        public void close() {
            context.close();
        }
    }

    private record RotationResult(String token, Throwable failure) {

        static RotationResult success(String token) {
            return new RotationResult(token, null);
        }

        static RotationResult failure(Throwable failure) {
            return new RotationResult(null, failure);
        }

        boolean success() {
            return failure == null;
        }
    }

    private record TokenSnapshot(
            Long id,
            Long userId,
            String selector,
            Instant expiresAt,
            boolean revoked,
            Long replacedById) {
    }

    private static final class TestJwtConfig implements JwtConfig {

        @Override
        public Duration getAccessTtl() {
            return Duration.ofMinutes(15);
        }

        @Override
        public Duration getRefreshTtl() {
            return REFRESH_TTL;
        }

        @Override
        public Duration getRotationGrace() {
            return ROTATION_GRACE;
        }

        @Override
        public String getHeader() {
            return "Authorization";
        }

        @Override
        public String getPrefix() {
            return "Bearer";
        }

        @Override
        public String getClaimAuthorities() {
            return "authorities";
        }

        @Override
        public String getRefreshCookieName() {
            return "refresh_token";
        }

        @Override
        public String getCookiePath() {
            return "/";
        }

        @Override
        public boolean isCookieSecure() {
            return true;
        }

        @Override
        public String getCookieSameSite() {
            return "Strict";
        }

        @Override
        public String getSecret() {
            return "01234567890123456789012345678901";
        }

        @Override
        public String getIssuer() {
            return "studio-test";
        }
    }
}
