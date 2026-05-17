package studio.one.platform.ai.adapters.vector;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class PgVectorJdbcMapperSqlContractTest {

    @Test
    void listQueriesSelectNullableDistanceForSharedRowMapper() throws Exception {
        assertThat(sql("LIST_BY_OBJECT_SQL"))
                .contains("NULL::double precision AS distance");
        assertThat(sql("LIST_BY_OBJECT_PAGE_SQL"))
                .contains("NULL::double precision AS distance");
        assertThat(sql("LIST_BY_OBJECT_PAGE_FILTERED_SQL"))
                .contains("NULL::double precision AS distance");
    }

    private static String sql(String fieldName) throws Exception {
        Field field = PgVectorJdbcMapper.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
