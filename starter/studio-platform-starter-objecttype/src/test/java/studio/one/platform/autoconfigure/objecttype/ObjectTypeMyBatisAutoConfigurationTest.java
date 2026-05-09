package studio.one.platform.autoconfigure.objecttype;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import studio.one.platform.mybatis.autoconfigure.StudioMyBatisAutoConfiguration;
import studio.one.platform.objecttype.db.ObjectTypeStore;
import studio.one.platform.objecttype.db.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.db.model.ObjectTypeRow;
import studio.one.platform.objecttype.db.mybatis.ObjectTypeMapper;
import studio.one.platform.objecttype.db.mybatis.ObjectTypeMyBatisStore;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;
import studio.one.platform.objecttype.service.ObjectTypeAdminService;

class ObjectTypeMyBatisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
                    StudioMyBatisAutoConfiguration.class,
                    MybatisAutoConfiguration.class,
                    ObjectTypeAutoConfiguration.class))
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "spring.datasource.generate-unique-name=true",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "studio.features.objecttype.enabled=true",
                    "studio.objecttype.mode=db",
                    "studio.features.objecttype.persistence=mybatis");

    @Test
    void registersMyBatisObjectTypeStore() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ObjectTypeMapper.class);
            assertThat(context).hasSingleBean(ObjectTypeMyBatisStore.class);
            assertThat(context).hasSingleBean(ObjectTypeStore.class);
            assertThat(context).hasBean("objectTypeRegistry");
            assertThat(context).hasBean("objectPolicyResolver");
            assertThat(context).getBean(ObjectTypeRegistry.class).isNotNull();
            assertThat(context).getBean(ObjectPolicyResolver.class).isNotNull();
            assertThat(context).hasSingleBean(ObjectTypeAdminService.class);
        });
    }

    @Test
    void executesMyBatisMapperXmlStatements() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getBean(javax.sql.DataSource.class));
            createSchema(jdbcTemplate);
            jdbcTemplate.update("""
                    INSERT INTO tb_application_object_type (
                      OBJECT_TYPE, CODE, NAME, DOMAIN, STATUS, DESCRIPTION,
                      CREATED_BY, CREATED_BY_ID, CREATED_AT,
                      UPDATED_BY, UPDATED_BY_ID, UPDATED_AT
                    ) VALUES (
                      1001, 'attachment', 'Attachment', 'system', 'active', 'default',
                      'tester', 1, CURRENT_TIMESTAMP,
                      'tester', 1, CURRENT_TIMESTAMP
                    )
                    """);

            ObjectTypeMapper mapper = context.getBean(ObjectTypeMapper.class);
            ObjectTypeRow newRow = new ObjectTypeRow();
            newRow.setObjectType(1002);
            newRow.setCode("wiki");
            newRow.setName("Wiki");
            newRow.setDomain("system");
            newRow.setStatus("active");
            newRow.setCreatedBy("tester");
            newRow.setCreatedById(1);
            newRow.setUpdatedBy("tester");
            newRow.setUpdatedById(1);
            mapper.upsertType(newRow);

            ObjectTypeRow selected = mapper.selectByType(1001);
            assertThat(selected).isNotNull();
            assertThat(selected.getCode()).isEqualTo("attachment");
            assertThat(mapper.count("system", "active", "%i%")).isEqualTo(1);
            assertThat(mapper.search("system", "active", "%i%", 10, 0L))
                    .extracting(ObjectTypeRow::getCode)
                    .containsExactly("wiki");

            ObjectTypeRow patch = new ObjectTypeRow();
            patch.setName("Attachment Updated");
            patch.setUpdatedBy("tester");
            patch.setUpdatedById(1);
            mapper.patchType(1001, patch, java.sql.Timestamp.from(java.time.Instant.now()));

            assertThat(mapper.selectByCode("attachment").getName()).isEqualTo("Attachment Updated");

            ObjectTypePolicyRow policy = new ObjectTypePolicyRow();
            policy.setObjectType(1002);
            policy.setMaxFileMb(50);
            policy.setAllowedExt("pdf");
            policy.setAllowedMime("application/pdf");
            policy.setPolicyJson("{}");
            policy.setCreatedBy("tester");
            policy.setCreatedById(1);
            policy.setUpdatedBy("tester");
            policy.setUpdatedById(1);
            mapper.upsertPolicy(policy);
            assertThat(mapper.selectPolicyByType(1002).getMaxFileMb()).isEqualTo(50);

            mapper.delete(1002);
            assertThat(mapper.selectByType(1002)).isNull();
        });
    }

    @Test
    void acceptsCustomNamedSqlSessionTemplate() {
        contextRunner
                .withUserConfiguration(CustomSqlSessionTemplateConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("customSqlSessionTemplate");
                    assertThat(context).hasSingleBean(ObjectTypeMapper.class);
                });
    }

    @Test
    void failsFastWhenMyBatisPersistenceHasNoSqlSessionTemplate() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ObjectTypeAutoConfiguration.class))
                .withPropertyValues(
                        "studio.features.objecttype.enabled=true",
                        "studio.objecttype.mode=db",
                        "studio.features.objecttype.persistence=mybatis")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ObjectType persistence=mybatis requires MyBatis infrastructure");
                });
    }

    @Test
    void failsFastWhenObjectTypeMapperXmlIsMissing() {
        contextRunner
                .withPropertyValues("mybatis.mapper-locations=classpath*:missing-objecttype-mapper/**/*.xml")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Missing ObjectType MyBatis mapped statement");
                });
    }

    @Test
    void failsFastWhenDatabaseIdIsUnsupported() {
        contextRunner
                .withPropertyValues("studio.mybatis.database-id-aliases.H2=oracle")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ObjectType persistence=mybatis supports PostgreSQL, H2, MySQL, and MariaDB only")
                            .hasMessageContaining("oracle");
                });
    }

    private void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE tb_application_object_type (
                  OBJECT_TYPE INT PRIMARY KEY,
                  CODE VARCHAR(100),
                  NAME VARCHAR(200),
                  DOMAIN VARCHAR(100),
                  STATUS VARCHAR(50),
                  DESCRIPTION VARCHAR(500),
                  CREATED_BY VARCHAR(100),
                  CREATED_BY_ID BIGINT,
                  CREATED_AT TIMESTAMP,
                  UPDATED_BY VARCHAR(100),
                  UPDATED_BY_ID BIGINT,
                  UPDATED_AT TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE tb_application_object_type_policy (
                  OBJECT_TYPE INT PRIMARY KEY,
                  MAX_FILE_MB INT,
                  ALLOWED_EXT VARCHAR(500),
                  ALLOWED_MIME VARCHAR(500),
                  POLICY_JSON CLOB,
                  CREATED_BY VARCHAR(100),
                  CREATED_BY_ID BIGINT,
                  CREATED_AT TIMESTAMP,
                  UPDATED_BY VARCHAR(100),
                  UPDATED_BY_ID BIGINT,
                  UPDATED_AT TIMESTAMP
                )
                """);
    }

    @AutoConfigurationPackage(basePackageClasses = ObjectTypeMapper.class)
    static class TestApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSqlSessionTemplateConfig {

        @Bean("customSqlSessionTemplate")
        SqlSessionTemplate customSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }
    }
}
