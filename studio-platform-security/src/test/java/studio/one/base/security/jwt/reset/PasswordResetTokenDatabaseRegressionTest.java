package studio.one.base.security.jwt.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import studio.one.base.security.application.usecase.password.PasswordResetService;
import studio.one.base.security.jwt.reset.domain.model.PasswordResetToken;
import studio.one.base.security.jwt.reset.domain.port.PasswordResetTokenRepository;
import studio.one.base.security.jwt.reset.infrastructure.persistence.jdbc.PasswordResetTokenJdbcRepository;
import studio.one.base.security.jwt.reset.infrastructure.persistence.jdbc.PasswordResetTokenJdbcRepositoryV2;
import studio.one.base.security.jwt.reset.infrastructure.persistence.jpa.PasswordResetTokenJpaRepository;
import studio.one.base.user.application.command.MePasswordChangeCommand;
import studio.one.base.user.application.command.MeProfilePatchCommand;
import studio.one.base.user.application.command.MeProfilePutCommand;
import studio.one.base.user.application.result.BatchResult;
import studio.one.base.user.application.usecase.ApplicationUserService;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.Status;
import studio.one.base.user.domain.model.User;

@SpringJUnitConfig(classes = PasswordResetTokenDatabaseRegressionTest.Config.class)
class PasswordResetTokenDatabaseRegressionTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final Instant CREATED_AT = Instant.parse("2026-09-23T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2027-01-01T00:00:00Z");

    static {
        POSTGRES.start();
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PasswordResetTokenJpaRepository jpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private TestApplicationUserService userService;

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        userService.reset();
        recreateSchema();
    }

    @Test
    void jdbcV1ConcurrentConsumeAllowsOneWinner() throws Exception {
        PasswordResetTokenJdbcRepository repository = new PasswordResetTokenJdbcRepository(namedParameterJdbcTemplate);
        seedConcurrentTokens(repository);

        assertConcurrentConsumeAllowsOneWinner(repository, new DataSourceTransactionManager(dataSource), false);
    }

    @Test
    void jdbcV2ConcurrentConsumeAllowsOneWinner() throws Exception {
        PasswordResetTokenJdbcRepositoryV2 repository = new PasswordResetTokenJdbcRepositoryV2(namedParameterJdbcTemplate);
        seedConcurrentTokens(repository);

        assertConcurrentConsumeAllowsOneWinner(repository, new DataSourceTransactionManager(dataSource), false);
    }

    @Test
    void jpaConcurrentConsumeAllowsOneWinner() throws Exception {
        seedConcurrentTokens(jpaRepository);

        assertConcurrentConsumeAllowsOneWinner(jpaRepository, transactionManager, true);
    }

    @Test
    void jpaPasswordFailureRollsBackTokenClaim() {
        assertTrue(AopUtils.isAopProxy(passwordResetService));
        String rawToken = "1.rollback-token";
        PasswordResetToken token = saveJpaToken(1L, rawToken, CREATED_AT, false);
        userService.failAfterSideEffect();

        assertThrows(IllegalStateException.class,
                () -> passwordResetService.resetPassword(rawToken, "rolled-back-password"));

        assertFalse(readTokenUsed(token.getId()));
        assertNull(readPasswordMarker(1L));
    }

    @Test
    void usedNewestDoesNotExposeOlderToken() {
        String tokenA = "1.tokenA";
        String tokenB = "1.tokenB";
        PasswordResetToken older = saveJpaToken(1L, tokenA, CREATED_AT, false);
        PasswordResetToken newer = saveJpaToken(1L, tokenB, CREATED_AT.plusSeconds(1), false);

        passwordResetService.resetPassword(tokenB, "password-from-token-b");

        assertFalse(passwordResetService.validateToken(tokenA));
        assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(tokenA, "password-from-token-a"));
        assertFalse(readTokenUsed(older.getId()));
        assertTrue(readTokenUsed(newer.getId()));
        assertEquals("password-from-token-b", readPasswordMarker(1L));
    }

    @Test
    void equalTimestampSelectsHigherId() throws Exception {
        assertEqualTimestampSelectsHigherId(new PasswordResetTokenJdbcRepository(namedParameterJdbcTemplate),
                "jdbc-v1");

        recreateSchema();
        assertEqualTimestampSelectsHigherId(new PasswordResetTokenJdbcRepositoryV2(namedParameterJdbcTemplate),
                "jdbc-v2");

        recreateSchema();
        assertEqualTimestampSelectsHigherId(jpaRepository, "jpa");
    }

    private void assertConcurrentConsumeAllowsOneWinner(PasswordResetTokenRepository repository,
            PlatformTransactionManager manager, boolean verifyIndependentEntityManager) throws Exception {
        CountDownLatch readsReturned = new CountDownLatch(2);
        List<Long> readIds = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(consumeAttempt(repository, manager, readsReturned, readIds));
            Future<Boolean> second = executor.submit(consumeAttempt(repository, manager, readsReturned, readIds));

            long winners = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .stream()
                    .filter(Boolean::booleanValue)
                    .count();

            assertEquals(2, readIds.size());
            assertEquals(1, readIds.stream().distinct().count());
            assertEquals(1L, winners);
            assertEquals(1, countRows("select count(*) from TEST_PASSWORD_RESET_SIDE_EFFECT"));
            assertEquals(1, countRows("""
                    select count(*)
                      from TB_APPLICATION_PASSWORD_RESET_TOKEN
                     where USER_ID = 1 and USED = true
                    """));
            assertEquals(0, countRows("""
                    select count(*)
                      from TB_APPLICATION_PASSWORD_RESET_TOKEN
                     where USER_ID = 2 and USED = true
                    """));
            if (verifyIndependentEntityManager) {
                assertIndependentEntityManagerSeesOneUsedToken();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Boolean> consumeAttempt(PasswordResetTokenRepository repository,
            PlatformTransactionManager manager, CountDownLatch readsReturned, List<Long> readIds) {
        return () -> newTransaction(manager).execute(status -> {
            PasswordResetToken latest = repository.findLatestByUserId(1L).orElseThrow();
            readIds.add(latest.getId());
            readsReturned.countDown();
            try {
                assertTrue(readsReturned.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            boolean claimed = repository.claimLatestUnused(latest.getId(), latest.getUserId(), latest.getCreatedAt());
            if (claimed) {
                jdbcTemplate.update("""
                        insert into TEST_PASSWORD_RESET_SIDE_EFFECT (TOKEN_ID, USER_ID)
                        values (?, ?)
                        """, latest.getId(), latest.getUserId());
            }
            return claimed;
        });
    }

    private TransactionTemplate newTransaction(PlatformTransactionManager manager) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void assertIndependentEntityManagerSeesOneUsedToken() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            Long used = entityManager.createQuery("""
                    select count(token)
                      from PasswordResetToken token
                     where token.userId = :userId and token.used = true
                    """, Long.class)
                    .setParameter("userId", 1L)
                    .getSingleResult();
            assertEquals(1L, used);
        } finally {
            entityManager.close();
        }
    }

    private void assertEqualTimestampSelectsHigherId(PasswordResetTokenRepository repository, String label) {
        PasswordResetToken low = repository.save(token(1L, label + "-low", CREATED_AT, false));
        PasswordResetToken high = repository.save(token(1L, label + "-high", CREATED_AT, false));

        assertTrue(high.getId() > low.getId());
        assertEquals(high.getId(), repository.findLatestByUserId(1L).orElseThrow().getId());
        assertEquals(high.getId(), repository.findActiveByUserId(1L).orElseThrow().getId());
    }

    private void seedConcurrentTokens(PasswordResetTokenRepository repository) {
        PasswordResetToken current = repository.save(token(1L, "current-token", CREATED_AT, false));
        PasswordResetToken otherUser = repository.save(token(2L, "other-user-token", CREATED_AT.plusSeconds(1), false));

        assertNotNull(current.getId());
        assertNotNull(otherUser.getId());
    }

    private PasswordResetToken saveJpaToken(Long userId, String rawToken, Instant createdAt, boolean used) {
        return jpaRepository.saveAndFlush(token(userId, passwordEncoder.encode(rawToken), createdAt, used));
    }

    private PasswordResetToken token(Long userId, String token, Instant createdAt, boolean used) {
        return PasswordResetToken.builder()
                .userId(userId)
                .token(token)
                .expiresAt(EXPIRES_AT)
                .createdAt(createdAt)
                .used(used)
                .build();
    }

    private boolean readTokenUsed(Long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select USED
                  from TB_APPLICATION_PASSWORD_RESET_TOKEN
                 where ID = ?
                """, Boolean.class, id));
    }

    private String readPasswordMarker(Long userId) {
        return jdbcTemplate.queryForObject("""
                select PASSWORD_MARKER
                  from TB_APPLICATION_USER
                 where USER_ID = ?
                """, String.class, userId);
    }

    private int countRows(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private void recreateSchema() throws Exception {
        jdbcTemplate.execute("drop table if exists TEST_PASSWORD_RESET_SIDE_EFFECT cascade");
        jdbcTemplate.execute("drop table if exists TB_APPLICATION_PASSWORD_RESET_TOKEN cascade");
        jdbcTemplate.execute("drop table if exists TB_APPLICATION_REFRESH_TOKEN cascade");
        jdbcTemplate.execute("drop table if exists TB_LOGIN_FAILURE_LOG cascade");
        jdbcTemplate.execute("drop table if exists TB_APPLICATION_USER cascade");
        jdbcTemplate.execute("""
                create table TB_APPLICATION_USER (
                    USER_ID bigint primary key,
                    USERNAME varchar(255) not null unique,
                    PASSWORD_MARKER varchar(255)
                )
                """);
        executeSecurityMigration();
        jdbcTemplate.execute("""
                create table TEST_PASSWORD_RESET_SIDE_EFFECT (
                    ID bigserial primary key,
                    TOKEN_ID bigint not null,
                    USER_ID bigint not null
                )
                """);
        jdbcTemplate.update("""
                insert into TB_APPLICATION_USER (USER_ID, USERNAME, PASSWORD_MARKER)
                values (1, 'alice', null), (2, 'bob', null)
                """);
    }

    private void executeSecurityMigration() throws Exception {
        ClassPathResource resource = new ClassPathResource("schema/security/postgres/V400__create_security_tables.sql");
        String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String statement : splitPostgresStatements(script)) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
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

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = PasswordResetTokenJpaRepository.class)
    static class Config {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(),
                    POSTGRES.getPassword());
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
            factory.setDataSource(dataSource);
            factory.setJpaVendorAdapter(vendorAdapter);
            factory.setPackagesToScan(PasswordResetToken.class.getPackageName());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "none",
                    "hibernate.show_sql", "false"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory,
                DataSource dataSource) {
            JpaTransactionManager manager = new JpaTransactionManager(entityManagerFactory);
            manager.setDataSource(dataSource);
            return manager;
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        TestApplicationUserService userService(JdbcTemplate jdbcTemplate) {
            return new TestApplicationUserService(jdbcTemplate);
        }

        @Bean
        PasswordResetService passwordResetService(TestApplicationUserService userService,
                PasswordResetTokenRepository tokenRepository, PasswordEncoder passwordEncoder) {
            return new PasswordResetService(userService, tokenRepository, passwordEncoder, (to, token) -> {
            });
        }
    }

    static class TestApplicationUserService implements ApplicationUserService<User, Role> {

        private final JdbcTemplate jdbcTemplate;
        private boolean failAfterSideEffect;

        TestApplicationUserService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        void reset() {
            failAfterSideEffect = false;
        }

        void failAfterSideEffect() {
            failAfterSideEffect = true;
        }

        @Override
        public User get(Long userId) {
            return new TestUser(userId, "user-" + userId, "user-" + userId + "@example.test");
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.of(new TestUser(1L, "user-1", email));
        }

        @Override
        public void resetPassword(Long userId, String rawPassword, String actor, String reason) {
            int updated = jdbcTemplate.update("""
                    update TB_APPLICATION_USER
                       set PASSWORD_MARKER = ?
                     where USER_ID = ?
                    """, rawPassword, userId);
            if (updated != 1) {
                throw new IllegalStateException("password side effect did not update one row");
            }
            if (failAfterSideEffect) {
                throw new IllegalStateException("password side effect failed");
            }
        }

        @Override
        public Page<User> findAll(Pageable pageable) {
            throw unsupported();
        }

        @Override
        public Page<User> findByNameOrUsernameOrEmail(String keyword, Pageable pageable) {
            throw unsupported();
        }

        @Override
        public Optional<User> findByUsername(String username) {
            throw unsupported();
        }

        @Override
        public Optional<User> findEnabledByUsername(String username) {
            throw unsupported();
        }

        @Override
        public Optional<User> findEnabledById(Long userId) {
            throw unsupported();
        }

        @Override
        public Long findIdByUsername(String username) {
            throw unsupported();
        }

        @Override
        public User create(User user) {
            throw unsupported();
        }

        @Override
        public User update(Long userId, Consumer<User> mutator) {
            throw unsupported();
        }

        @Override
        public void delete(Long userId) {
            throw unsupported();
        }

        @Override
        public void enable(Long userId, String actor) {
            throw unsupported();
        }

        @Override
        public void disable(Long userId, String actor, String reason, OffsetDateTime until,
                boolean revokeTokens, boolean invalidateSessions, boolean notifyUser) {
            throw unsupported();
        }

        @Override
        public User updateSelfByUsername(String username, MeProfilePatchCommand command) {
            throw unsupported();
        }

        @Override
        public User replaceSelfByUsername(String username, MeProfilePutCommand command) {
            throw unsupported();
        }

        @Override
        public User changeSelfPasswordByUsername(String username, MePasswordChangeCommand command) {
            throw unsupported();
        }

        @Override
        public Page<User> search(String q, Pageable pageable) {
            throw unsupported();
        }

        @Override
        public Page<User> getUsersByGroup(Long groupId, Pageable pageable) {
            throw unsupported();
        }

        @Override
        public void assignRole(Long userId, Long roleId, String by) {
            throw unsupported();
        }

        @Override
        public void revokeRole(Long userId, Long roleId) {
            throw unsupported();
        }

        @Override
        public BatchResult updateUserRolesBulk(Long userId, List<Long> desired, String actor) {
            throw unsupported();
        }

        @Override
        public BatchResult assignRolesBulk(Long userId, List<Long> roles, String actor) {
            throw unsupported();
        }

        @Override
        public void joinGroup(Long userId, Long groupId, String by) {
            throw unsupported();
        }

        @Override
        public void leaveGroup(Long userId, Long groupId) {
            throw unsupported();
        }

        @Override
        public Map<String, String> getProperties(Long userId) {
            throw unsupported();
        }

        @Override
        public Map<String, String> replaceProperties(Long userId, Map<String, String> properties) {
            throw unsupported();
        }

        @Override
        public String getProperty(Long userId, String key) {
            throw unsupported();
        }

        @Override
        public String setProperty(Long userId, String key, String value) {
            throw unsupported();
        }

        @Override
        public void deleteProperty(Long userId, String key) {
            throw unsupported();
        }

        @Override
        public Set<Role> findEffectiveRoles(Long userId) {
            throw unsupported();
        }

        @Override
        public List<Role> getUserRoles(Long userId) {
            throw unsupported();
        }

        @Override
        public List<Role> getUserGroupsRoles(Long userId) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used by password reset regression tests");
        }
    }

    static class TestUser implements User {

        private final Long userId;
        private final String username;
        private final String email;
        private Map<String, String> properties = new HashMap<>();

        TestUser(Long userId, String username, String email) {
            this.userId = userId;
            this.username = username;
            this.email = email;
        }

        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getName() {
            return username;
        }

        @Override
        public String getFirstName() {
            return username;
        }

        @Override
        public String getLastName() {
            return username;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isNameVisible() {
            return true;
        }

        @Override
        public boolean isEmailVisible() {
            return true;
        }

        @Override
        public Status getStatus() {
            return Status.APPROVED;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean isAnonymous() {
            return false;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public int getFailedAttempts() {
            return 0;
        }

        @Override
        public boolean isAccountLockedNow(Instant now) {
            return false;
        }

        @Override
        public Instant getLastFailedAt() {
            return null;
        }

        @Override
        public Instant getAccountLockedUntil() {
            return null;
        }

        @Override
        public Instant getCreationDate() {
            return Instant.EPOCH;
        }

        @Override
        public Instant getModifiedDate() {
            return Instant.EPOCH;
        }

        @Override
        public boolean isExternal() {
            return false;
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }
}
