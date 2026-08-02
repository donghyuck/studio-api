package studio.one.platform.ai.adapters.vector.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PgVectorMapperXmlContractTest {

    @Test
    void objectScopedSearchCastsNullableScopeParametersForPostgres() throws Exception {
        String mapper = mapperXml();

        assertThat(mapper)
                .contains("<select id=\"searchByObject\"")
                .contains("<select id=\"hybridSearchByObject\"")
                .contains("<if test=\"objectType != null\">")
                .contains("<if test=\"objectId != null\">");
        assertThat(mapper.split(Pattern.quote(
                "CAST(#{objectId,jdbcType=VARCHAR} AS varchar) IS NULL OR object_id = CAST(#{objectId,jdbcType=VARCHAR} AS varchar)"),
                -1))
                .hasSize(6);
    }

    @Test
    void listByObjectSelectsNullableDistanceForSharedResultMap() throws Exception {
        String mapper = mapperXml();

        assertThat(mapper)
                .contains("<select id=\"listByObject\" resultMap=\"PgVectorSearchRowMap\">")
                .contains("<select id=\"listByObjectPage\" resultMap=\"PgVectorSearchRowMap\">")
                .contains("<select id=\"listByObjectPageFiltered\" resultMap=\"PgVectorSearchRowMap\">");
        assertThat(mapper.split(Pattern.quote("NULL::double precision AS distance"), -1))
                .hasSize(4);
    }

    @Test
    void filteredListByObjectPushesPreviewFiltersIntoSql() throws Exception {
        String mapper = mapperXml();

        assertThat(mapper)
                .contains("<sql id=\"listFilterConditions\">")
                .contains("metadata -&gt;&gt; 'documentId'")
                .contains("metadata -&gt;&gt; 'chunkId'")
                .contains("metadata -&gt;&gt; 'headingPath'")
                .contains("LIMIT #{limit} OFFSET #{offset}");
    }

    @Test
    void upsertChunksUsesMultiRowInsertContract() throws Exception {
        String mapper = mapperXml();

        assertThat(mapper)
                .contains("<insert id=\"upsertChunks\">")
                .contains("<foreach collection=\"chunks\" item=\"chunk\" separator=\",\">")
                .contains("ON CONFLICT (object_type, object_id, partition_id, chunk_index)")
                .contains("DO UPDATE SET");
    }

    @Test
    void partitionOperationsUsePhysicalPartitionColumn() throws Exception {
        String mapper = mapperXml();

        assertThat(mapper)
                .contains("-&gt;&gt;'partitionId'")
                .contains("<delete id=\"deleteByObjectPartition\">")
                .contains("partition_id = #{partitionId,jdbcType=VARCHAR}");
    }

    private String mapperXml() throws Exception {
        return new String(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResourceAsStream("mybatis/ai/PgVectorMapper.xml"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
