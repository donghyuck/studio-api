package studio.one.platform.ai.adapters.vector.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PgVectorMapperXmlContractTest {

    @Test
    void objectScopedSearchCastsNullableScopeParametersForPostgres() throws Exception {
        String mapper = new String(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResourceAsStream("mybatis/ai/PgVectorMapper.xml"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(mapper)
                .contains("<select id=\"searchByObject\"")
                .contains("<select id=\"hybridSearchByObject\"");
        assertThat(mapper.split(Pattern.quote(
                "CAST(#{objectType,jdbcType=VARCHAR} AS varchar) IS NULL OR object_type = CAST(#{objectType,jdbcType=VARCHAR} AS varchar)"),
                -1))
                .hasSize(3);
        assertThat(mapper.split(Pattern.quote(
                "CAST(#{objectId,jdbcType=VARCHAR} AS varchar) IS NULL OR object_id = CAST(#{objectId,jdbcType=VARCHAR} AS varchar)"),
                -1))
                .hasSize(3);
    }
}
