package studio.one.platform.autoconfigure.objecttype;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class ObjectTypeMapperXmlContractTest {

    @Test
    void upsertStatementsCoverSupportedDatabaseIds() throws Exception {
        String mapper = new String(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResourceAsStream("mybatis/objecttype/ObjectTypeMapper.xml"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(mapper)
                .contains("<insert id=\"upsertType\"")
                .contains("<insert id=\"upsertType\" databaseId=\"h2\"")
                .contains("<insert id=\"upsertType\" databaseId=\"mysql\"")
                .contains("<insert id=\"upsertType\" databaseId=\"mariadb\"")
                .contains("<insert id=\"upsertPolicy\"")
                .contains("<insert id=\"upsertPolicy\" databaseId=\"h2\"")
                .contains("<insert id=\"upsertPolicy\" databaseId=\"mysql\"")
                .contains("<insert id=\"upsertPolicy\" databaseId=\"mariadb\"");
    }
}
