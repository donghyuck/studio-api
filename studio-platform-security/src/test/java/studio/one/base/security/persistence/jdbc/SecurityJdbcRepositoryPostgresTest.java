package studio.one.base.security.persistence.jdbc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import studio.one.base.security.audit.domain.model.LoginFailureLog;
import studio.one.base.security.audit.infrastructure.persistence.jdbc.LoginFailureLogJdbcRepository;
import studio.one.base.security.audit.application.command.LoginFailQuery;
import studio.one.base.security.authentication.lock.infrastructure.persistence.jdbc.AccountLockJdbcRepository;
import studio.one.base.security.jwt.refresh.domain.model.RefreshToken;
import studio.one.base.security.jwt.refresh.infrastructure.persistence.jdbc.RefreshTokenJdbcRepositoryV2;
import studio.one.base.security.jwt.reset.domain.model.PasswordResetToken;
import studio.one.base.security.jwt.reset.infrastructure.persistence.jdbc.PasswordResetTokenJdbcRepositoryV2;
@Testcontainers(disabledWithoutDocker = true)
class SecurityJdbcRepositoryPostgresTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private DriverManagerDataSource dataSource;
    private NamedParameterJdbcTemplate template;
    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
        template = new NamedParameterJdbcTemplate(dataSource);
        recreateSchema();
    }
    @Test
    void refreshTokenRepositoryPersistsUpdatesAndFindsBySelector() {
        RefreshTokenJdbcRepositoryV2 repository = new RefreshTokenJdbcRepositoryV2(template);
        RefreshToken token = RefreshToken.builder()
                .userId(1L)
                .selector("selector-1")
                .verifierHash("verifier")
                .expiresAt(Instant.parse("2026-05-09T00:00:00Z"))
                .createdAt(Instant.parse("2026-05-08T00:00:00Z"))
                .build();
        RefreshToken saved = repository.save(token);
        assertNotNull(saved.getId());
        saved.setRevoked(true);
        repository.save(saved);
        RefreshToken found = repository.findBySelector("selector-1").orElseThrow();
        assertEquals(saved.getId(), found.getId());
        assertTrue(found.isRevoked());
    }
    @Test
    void passwordResetTokenRepositoryPersistsUpdatesAndFindsActiveToken() {
        PasswordResetTokenJdbcRepositoryV2 repository = new PasswordResetTokenJdbcRepositoryV2(template);
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(1L)
                .token("reset-token")
                .expiresAt(Instant.parse("2026-05-09T00:00:00Z"))
                .createdAt(Instant.parse("2026-05-08T00:00:00Z"))
                .used(false)
                .build();
        PasswordResetToken saved = repository.save(token);
        assertNotNull(saved.getId());
        assertEquals("reset-token", repository.findActiveByUserId(1L).orElseThrow().getToken());
        saved.setUsed(true);
        repository.save(saved);
        assertFalse(repository.findActiveByUserId(1L).isPresent());
    }
    @Test
    void accountLockRepositoryUpdatesAndReadsLockState() {
        AccountLockJdbcRepository repository = new AccountLockJdbcRepository(template);
        Instant now = Instant.parse("2026-05-08T00:00:00Z");
        Instant until = Instant.parse("2026-05-08T01:00:00Z");
        assertEquals(1, repository.bumpFailedAttempts("kim.owner", now));
        assertEquals(1, repository.findFailedAttempts("kim.owner"));
        assertEquals(now, repository.findLastFailedAt("kim.owner"));
        assertEquals(1, repository.lockUntil("kim.owner", until));
        assertEquals(until, repository.findAccountLockedUntil("kim.owner"));
        assertEquals(1, repository.resetLockState("kim.owner"));
        assertEquals(0, repository.findFailedAttempts("kim.owner"));
        assertEquals(null, repository.findLastFailedAt("kim.owner"));
        assertEquals(null, repository.findAccountLockedUntil("kim.owner"));
    }
    @Test
    void loginFailureRepositoryPersistsSearchesAndDeletesWithInetCast() {
        LoginFailureLogJdbcRepository repository = new LoginFailureLogJdbcRepository(template);
        LoginFailureLog log = LoginFailureLog.builder()
                .username("kim.owner")
                .remoteIp("127.0.0.1")
                .userAgent("JUnit")
                .failureType("BAD_CREDENTIALS")
                .message("bad password")
                .occurredAt(Instant.parse("2026-05-08T00:00:00Z"))
                .build();
        LoginFailureLog saved = repository.save(log);
        assertNotNull(saved.getId());
        assertEquals("127.0.0.1", saved.getRemoteIp());
        assertEquals(1, repository.countByUsernameSince("kim.owner", Instant.parse("2026-05-07T00:00:00Z")));
        saved.setMessage("updated");
        repository.save(saved);
        LoginFailQuery query = LoginFailQuery.builder()
                .usernameLike("kim")
                .ipEquals("127.0.0.1")
                .failureType("BAD_CREDENTIALS")
                .build();
        var page = repository.search(query, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("updated", page.getContent().get(0).getMessage());
        LoginFailQuery mappedIpv6Query = LoginFailQuery.builder()
                .ipEquals("::ffff:127.0.0.1")
                .build();
        assertEquals(1, repository.search(mappedIpv6Query, PageRequest.of(0, 10)).getTotalElements());
        LoginFailureLog mappedRemoteIp = LoginFailureLog.builder()
                .username("mapped.ip")
                .remoteIp("::ffff:192.0.2.128")
                .userAgent("JUnit")
                .failureType("BAD_CREDENTIALS")
                .message("mapped ip")
                .occurredAt(Instant.parse("2026-05-08T00:30:00Z"))
                .build();
        assertEquals("192.0.2.128", repository.save(mappedRemoteIp).getRemoteIp());
        LoginFailureLog invalidRemoteIp = LoginFailureLog.builder()
                .username("bad.ip")
                .remoteIp("not-an-ip")
                .userAgent("JUnit")
                .failureType("BAD_CREDENTIALS")
                .message("bad ip")
                .occurredAt(Instant.parse("2026-05-08T01:00:00Z"))
                .build();
        LoginFailureLog savedInvalidRemoteIp = repository.save(invalidRemoteIp);
        assertNotNull(savedInvalidRemoteIp.getId());
        assertNull(savedInvalidRemoteIp.getRemoteIp());
        var invalidIpPage = repository.search(LoginFailQuery.builder().usernameLike("bad.ip").build(),
                PageRequest.of(0, 10));
        assertEquals(1, invalidIpPage.getTotalElements());
        assertNull(invalidIpPage.getContent().get(0).getRemoteIp());
        LoginFailureLog oversizedMetadata = LoginFailureLog.builder()
                .username("long.metadata")
                .remoteIp("127.0.0.2")
                .userAgent("a".repeat(600))
                .failureType("f".repeat(200))
                .message("m".repeat(1200))
                .occurredAt(Instant.parse("2026-05-08T02:00:00Z"))
                .build();
        LoginFailureLog savedOversizedMetadata = repository.save(oversizedMetadata);
        assertEquals("127.0.0.2", savedOversizedMetadata.getRemoteIp());
        assertEquals(512, savedOversizedMetadata.getUserAgent().length());
        assertEquals(128, savedOversizedMetadata.getFailureType().length());
        assertEquals(1000, savedOversizedMetadata.getMessage().length());
        var oversizedPage = repository.search(LoginFailQuery.builder().usernameLike("long.metadata").build(),
                PageRequest.of(0, 10));
        assertEquals(1, oversizedPage.getTotalElements());
        assertEquals(512, oversizedPage.getContent().get(0).getUserAgent().length());
        assertEquals(128, oversizedPage.getContent().get(0).getFailureType().length());
        assertEquals(1000, oversizedPage.getContent().get(0).getMessage().length());
        assertEquals(4, repository.deleteOlderThan(Instant.parse("2026-05-09T00:00:00Z")));
    }
    private void recreateSchema() throws Exception {
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_PASSWORD_RESET_TOKEN cascade");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_REFRESH_TOKEN cascade");
        template.getJdbcTemplate().execute("drop table if exists TB_LOGIN_FAILURE_LOG cascade");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_USER cascade");
        template.getJdbcTemplate().execute("create table TB_APPLICATION_USER (\n"
                + "    USER_ID bigint primary key,\n"
                + "    USERNAME varchar(255) not null unique,\n"
                + "    FAILED_ATTEMPTS integer not null default 0,\n"
                + "    LAST_FAILED_AT timestamptz,\n"
                + "    ACCOUNT_LOCKED_UNTIL timestamptz\n"
                + ")\n");
        executeSecurityMigration();
        template.getJdbcTemplate().update("insert into TB_APPLICATION_USER\n"
                + "    (USER_ID, USERNAME, FAILED_ATTEMPTS)\n"
                + "values\n"
                + "    (1, 'kim.owner', 0)\n");
    }
    private void executeSecurityMigration() throws Exception {
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
}
