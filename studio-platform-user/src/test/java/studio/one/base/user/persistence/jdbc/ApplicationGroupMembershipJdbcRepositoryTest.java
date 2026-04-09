package studio.one.base.user.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ApplicationGroupMembershipJdbcRepositoryTest {

    @Test
    void normalizeQuerySupportsBlankAndPartialMatch() throws Exception {
        Method method = ApplicationGroupMembershipJdbcRepository.class.getDeclaredMethod("normalize", String.class);
        method.setAccessible(true);

        assertEquals("", method.invoke(null, new Object[] { null }));
        assertEquals("", method.invoke(null, "   "));
        assertEquals("%alice%", method.invoke(null, " Alice "));
    }

    @Test
    void summarySortMappingIncludesExpectedColumns() {
        ApplicationGroupMembershipJdbcRepository repository = new ApplicationGroupMembershipJdbcRepository(
                mock(NamedParameterJdbcTemplate.class));

        String orderBy = repository.buildOrderByClause(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.asc("username"),
                        org.springframework.data.domain.Sort.Order.desc("enabled")),
                "u.USER_ID",
                Map.of(
                        "userId", "u.USER_ID",
                        "username", "u.USERNAME",
                        "name", "u.NAME",
                        "enabled", "u.ENABLED"));

        assertTrue(orderBy.contains("u.USERNAME asc"));
        assertTrue(orderBy.contains("u.ENABLED desc"));
    }
}
