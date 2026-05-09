package studio.one.platform.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.base.security.authentication.lock.persistence.AccountLockRepository;
import studio.one.base.security.authentication.lock.persistence.jdbc.AccountLockJdbcRepository;

class AccountLockAutoConfigurationTest {

    @Test
    void jdbcRepositoryDoesNotRequirePostgresGuard() {
        AccountLockAutoConfiguration.AccountLockJdbcConfig config =
                new AccountLockAutoConfiguration.AccountLockJdbcConfig();

        AccountLockRepository repository = assertDoesNotThrow(
                () -> config.accountLockJdbcRepository(mock(NamedParameterJdbcTemplate.class)));

        assertThat(repository).isInstanceOf(AccountLockJdbcRepository.class);
    }
}
