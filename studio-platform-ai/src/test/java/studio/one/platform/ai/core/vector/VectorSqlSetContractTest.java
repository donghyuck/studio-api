package studio.one.platform.ai.core.vector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class VectorSqlSetContractTest {

    @Test
    void objectScopedSearchCastsNullableScopeParametersForPostgres() throws IOException {
        String sqlset = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("sql/ai-sqlset.xml"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(sqlset)
                .contains("sql-query id=\"searchByObject\"")
                .contains("sql-query id=\"hybridSearchByObject\"");
        assertThat(sqlset.split("CAST\\(:objectType AS varchar\\) IS NULL OR object_type = CAST\\(:objectType AS varchar\\)", -1))
                .hasSize(3);
        assertThat(sqlset.split("CAST\\(:objectId AS varchar\\) IS NULL OR object_id = CAST\\(:objectId AS varchar\\)", -1))
                .hasSize(3);
    }
}
