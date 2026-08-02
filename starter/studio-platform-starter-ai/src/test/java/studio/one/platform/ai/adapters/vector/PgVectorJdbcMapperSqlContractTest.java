package studio.one.platform.ai.adapters.vector;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgvector.PGvector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMetadataEqualsCriterion;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorSearchParameter;

class PgVectorJdbcMapperSqlContractTest {

    @Test
    void listQueriesSelectNullableDistanceForSharedRowMapper() throws Exception {
        assertThat(sql("LIST_BY_OBJECT_SQL"))
                .contains("SELECT id, object_id")
                .contains("NULL::double precision AS distance");
        assertThat(sql("LIST_BY_OBJECT_PAGE_SQL"))
                .contains("SELECT id, object_id")
                .contains("NULL::double precision AS distance");
        assertThat(sql("LIST_BY_OBJECT_PAGE_FILTERED_SQL"))
                .contains("SELECT id, object_id")
                .contains("NULL::double precision AS distance")
                .doesNotContain(":documentId");
    }

    @Test
    void countByObjectQueryUsesObjectScope() throws Exception {
        assertThat(sql("COUNT_BY_OBJECT_SQL"))
                .contains("COUNT(*)")
                .contains("object_type = :objectType")
                .contains("object_id = CAST(:objectId AS varchar)");
    }

    @Test
    void metadataPatchChangesOnlyMetadataWithinObjectScope() throws Exception {
        assertThat(sql("PATCH_METADATA_BY_OBJECT_SQL"))
                .contains("SET metadata =")
                .contains("object_type = :objectType")
                .contains("object_id = :objectId")
                .doesNotContain("embedding =")
                .doesNotContain("embedding_dimension =")
                .doesNotContain("text =");
    }

    @Test
    void partitionDeleteUsesPhysicalPartitionColumnWithinObjectScope() throws Exception {
        assertThat(sql("DELETE_BY_OBJECT_PARTITION_SQL"))
                .contains("object_type = :objectType")
                .contains("object_id = :objectId")
                .contains("partition_id = :partitionId");
    }

    @Test
    void filteredSearchSqlSeparatesMetadataParametersFromOrderByClause() throws Exception {
        String filteredSql = filteredSql("SEARCH_BY_OBJECT_SQL", new PgVectorSearchParameter(
                new PGvector(new float[] {0.1f, 0.2f}),
                2,
                10,
                "attachment",
                "2",
                null,
                null,
                List.of(
                        new PgVectorMetadataEqualsCriterion("objectType", "attachment"),
                        new PgVectorMetadataEqualsCriterion("objectId", "2"),
                        new PgVectorMetadataEqualsCriterion("embeddingProvider", "kure"),
                        new PgVectorMetadataEqualsCriterion("embeddingModel", "nlpai-lab/KURE-v1")),
                List.of()));

        assertThat(filteredSql)
                .contains(":metadataEqualsValue3")
                .doesNotContain(":metadataEqualsValue3ORDER")
                .containsPattern(":metadataEqualsValue3\\s+ORDER BY");
    }

    private static String sql(String fieldName) throws Exception {
        Field field = PgVectorJdbcMapper.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static String filteredSql(String fieldName, PgVectorSearchParameter parameter) throws Exception {
        Method method = PgVectorJdbcMapper.class.getDeclaredMethod(
                "filteredSql",
                String.class,
                PgVectorSearchParameter.class);
        method.setAccessible(true);
        return (String) method.invoke(null, sql(fieldName), parameter);
    }
}
