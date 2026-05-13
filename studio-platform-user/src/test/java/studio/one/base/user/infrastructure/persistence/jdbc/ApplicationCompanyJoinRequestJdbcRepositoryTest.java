package studio.one.base.user.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyMemberKeyStatus;
import studio.one.base.user.domain.model.company.CompanyMemberStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.ApplicationCompanyJoinRequest;
import studio.one.base.user.domain.model.ApplicationCompanyMember;
import studio.one.base.user.domain.model.ApplicationCompanyMemberId;
import studio.one.base.user.domain.model.ApplicationCompanyMemberKey;

@Testcontainers
class ApplicationCompanyJoinRequestJdbcRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private NamedParameterJdbcTemplate template;
    private ApplicationCompanyMemberKeyJdbcRepository keyRepository;
    private ApplicationCompanyJoinRequestJdbcRepository requestRepository;
    private ApplicationCompanyMemberJdbcRepository memberRepository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
        template = new NamedParameterJdbcTemplate(dataSource);
        keyRepository = new ApplicationCompanyMemberKeyJdbcRepository(template);
        requestRepository = new ApplicationCompanyJoinRequestJdbcRepository(template);
        memberRepository = new ApplicationCompanyMemberJdbcRepository(template);
        recreateSchema();
    }

    @Test
    void storesLocksUpdatesAndQueriesMemberKeysAndJoinRequests() {
        ApplicationCompanyMemberKey key = key("b".repeat(64), 2);

        ApplicationCompanyMemberKey savedKey = keyRepository.save(key);
        savedKey = keyRepository.findByKeyHash("b".repeat(64)).orElseThrow();
        savedKey.setUsedCount(1);
        keyRepository.save(savedKey);

        assertThat(savedKey.getKeyId()).isPositive();
        assertThat(keyRepository.findByKeyHash("b".repeat(64))).isPresent();
        assertThat(keyRepository.findForUpdateByKeyHash("b".repeat(64))).isPresent();
        assertThat(keyRepository.findForUpdateById(savedKey.getKeyId()).orElseThrow().getUsedCount()).isEqualTo(1);

        ApplicationCompanyJoinRequest savedRequest = requestRepository.save(request(savedKey.getKeyId(), 7L, "joiner@example.com"));
        Long keyId = savedKey.getKeyId();

        assertThat(savedRequest.getRequestId()).isPositive();
        assertThat(requestRepository.findAllByCompanyId(10L, null, PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(requestRepository.findAllByCompanyId(10L, CompanyJoinRequestStatus.PENDING, PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(requestRepository.findForUpdateById(savedRequest.getRequestId())).isPresent();
        assertThat(requestRepository.existsPendingByKeyIdAndUserId(savedKey.getKeyId(), 7L)).isTrue();
        assertThat(requestRepository.existsPendingByCompanyIdAndUserId(10L, 7L)).isTrue();
        assertThat(requestRepository.countPendingByKeyId(savedKey.getKeyId())).isEqualTo(1);

        requestRepository.save(request(savedKey.getKeyId(), null, "deleted-user@example.com"));
        assertThat(requestRepository.countPendingByKeyId(savedKey.getKeyId())).isEqualTo(1);

        assertThatThrownBy(() -> requestRepository.save(request(keyId, 7L, "other@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);

        savedRequest.setStatus(CompanyJoinRequestStatus.APPROVED);
        savedRequest.setDecidedAt(Instant.now());
        savedRequest.setDecidedBy(99L);
        requestRepository.save(savedRequest);

        assertThat(requestRepository.existsPendingByCompanyIdAndUserId(10L, 7L)).isFalse();
        assertThat(requestRepository.countPendingByKeyId(savedKey.getKeyId())).isZero();
    }

    @Test
    void countsCompanyMembersByRole() {
        memberRepository.save(member(1L, CompanyRole.OWNER));
        memberRepository.save(member(2L, CompanyRole.OWNER));
        memberRepository.save(member(3L, CompanyRole.ADMIN));

        assertThat(memberRepository.countByCompanyIdAndRole(10L, CompanyRole.OWNER)).isEqualTo(2);
        assertThat(memberRepository.countByCompanyIdAndRole(10L, CompanyRole.ADMIN)).isEqualTo(1);
        assertThat(memberRepository.countByCompanyIdAndRole(10L, CompanyRole.MEMBER)).isZero();
    }

    private ApplicationCompanyMemberKey key(String hash, Integer maxUses) {
        ApplicationCompanyMemberKey key = new ApplicationCompanyMemberKey();
        key.setCompanyId(10L);
        key.setRole(CompanyRole.ADMIN);
        key.setKeyHash(hash);
        key.setStatus(CompanyMemberKeyStatus.ACTIVE);
        key.setExpiresAt(Instant.now().plusSeconds(3600));
        key.setMaxUses(maxUses);
        key.setCreatedBy(99L);
        key.setUpdatedBy(99L);
        return key;
    }

    private ApplicationCompanyJoinRequest request(Long keyId, Long userId, String email) {
        ApplicationCompanyJoinRequest request = new ApplicationCompanyJoinRequest();
        request.setCompanyId(10L);
        request.setKeyId(keyId);
        request.setUserId(userId);
        request.setName("Joiner");
        request.setEmail(email);
        request.setMessage("hello");
        request.setRequestedRole(CompanyRole.ADMIN);
        request.setStatus(CompanyJoinRequestStatus.PENDING);
        request.setRequestedBy(userId);
        return request;
    }

    private ApplicationCompanyMember member(Long userId, CompanyRole role) {
        ApplicationCompanyMember member = new ApplicationCompanyMember();
        member.setId(new ApplicationCompanyMemberId(10L, userId));
        member.setRole(role);
        member.setStatus(CompanyMemberStatus.ACTIVE);
        member.setJoinedBy(99L);
        member.setUpdatedBy(99L);
        return member;
    }

    private void recreateSchema() {
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_COMPANY_JOIN_REQUEST");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_COMPANY_MEMBER_KEY");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_COMPANY_MEMBERS");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_USER");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_COMPANY");
        template.getJdbcTemplate().execute("create table TB_APPLICATION_COMPANY (\\n" + "    COMPANY_ID bigint primary key,\\n" + "    STATUS varchar(30) not null default 'ACTIVE'\\n" + ")\\n");
        template.getJdbcTemplate().execute("create table TB_APPLICATION_USER (\\n" + "    USER_ID bigint primary key\\n" + ")\\n");
        template.getJdbcTemplate().execute("create table TB_APPLICATION_COMPANY_MEMBER_KEY (\\n" + "    KEY_ID bigserial primary key,\\n" + "    COMPANY_ID bigint not null,\\n" + "    ROLE varchar(30) not null,\\n" + "    KEY_HASH varchar(64) not null unique,\\n" + "    STATUS varchar(30) not null default 'ACTIVE',\\n" + "    EXPIRES_AT timestamptz,\\n" + "    MAX_USES integer,\\n" + "    USED_COUNT integer not null default 0,\\n" + "    CREATED_AT timestamptz not null default now(),\\n" + "    CREATED_BY bigint,\\n" + "    UPDATED_AT timestamptz,\\n" + "    UPDATED_BY bigint\\n" + ")\\n");
        template.getJdbcTemplate().execute("create table TB_APPLICATION_COMPANY_JOIN_REQUEST (\\n" + "    REQUEST_ID bigserial primary key,\\n" + "    COMPANY_ID bigint not null,\\n" + "    KEY_ID bigint not null,\\n" + "    USER_ID bigint,\\n" + "    REQUEST_NAME varchar(255),\\n" + "    EMAIL varchar(255),\\n" + "    MESSAGE varchar(1000),\\n" + "    REQUESTED_ROLE varchar(30) not null,\\n" + "    STATUS varchar(30) not null default 'PENDING',\\n" + "    REQUESTED_AT timestamptz not null default now(),\\n" + "    REQUESTED_BY bigint,\\n" + "    DECIDED_AT timestamptz,\\n" + "    DECIDED_BY bigint,\\n" + "    UPDATED_AT timestamptz\\n" + ")\\n");
        template.getJdbcTemplate().execute("create table TB_APPLICATION_COMPANY_MEMBERS (\\n" + "    COMPANY_ID bigint not null,\\n" + "    USER_ID bigint not null,\\n" + "    ROLE varchar(30) not null,\\n" + "    STATUS varchar(30) not null default 'ACTIVE',\\n" + "    JOINED_AT timestamptz,\\n" + "    JOINED_BY bigint,\\n" + "    UPDATED_AT timestamptz,\\n" + "    UPDATED_BY bigint,\\n" + "    primary key (COMPANY_ID, USER_ID)\\n" + ")\\n");
        template.getJdbcTemplate().execute("create unique index UK_COMPANY_JOIN_REQUEST_PENDING_USER\\n" + "    on TB_APPLICATION_COMPANY_JOIN_REQUEST (COMPANY_ID, USER_ID)\\n" + "    where STATUS = 'PENDING' and USER_ID is not null\\n");
        template.getJdbcTemplate().execute("insert into TB_APPLICATION_COMPANY (COMPANY_ID) values (10)");
        template.getJdbcTemplate().execute("insert into TB_APPLICATION_USER (USER_ID) values (7)");
    }
}
