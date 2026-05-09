package studio.one.platform.autoconfigure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(OutputCaptureExtension.class)
@SuppressWarnings("deprecation")
class EnvironmentPersistenceTypeResolverTest {

    @Test
    void resolvesFeaturePersistenceBeforeGlobal() {
        EnvironmentPersistenceTypeResolver resolver = new EnvironmentPersistenceTypeResolver(
                new MockEnvironment()
                        .withProperty("studio.features.workspace.persistence", "mybatis")
                        .withProperty("studio.persistence.type", "jpa"));

        assertThat(resolver.resolve("workspace", StudioPersistenceType.JPA))
                .isEqualTo(StudioPersistenceType.MYBATIS);
    }

    @Test
    void resolvesExplicitJpaValue() {
        EnvironmentPersistenceTypeResolver resolver = new EnvironmentPersistenceTypeResolver(
                new MockEnvironment()
                        .withProperty("studio.features.workspace.persistence", "jpa")
                        .withProperty("studio.persistence.type", "mybatis"));

        assertThat(resolver.resolve("workspace", StudioPersistenceType.MYBATIS))
                .isEqualTo(StudioPersistenceType.JPA);
    }

    @Test
    void resolvesGlobalPersistenceWhenFeatureMissing() {
        EnvironmentPersistenceTypeResolver resolver = new EnvironmentPersistenceTypeResolver(
                new MockEnvironment().withProperty("studio.persistence.type", "mybatis"));

        assertThat(resolver.resolve("workspace", StudioPersistenceType.JPA))
                .isEqualTo(StudioPersistenceType.MYBATIS);
    }

    @Test
    void normalizesJdbcAliasAndWarns(CapturedOutput output) {
        EnvironmentPersistenceTypeResolver resolver = new EnvironmentPersistenceTypeResolver(
                new MockEnvironment().withProperty("studio.features.workspace.persistence", "jdbc"));

        assertThat(resolver.resolve("workspace", StudioPersistenceType.JPA))
                .isEqualTo(StudioPersistenceType.MYBATIS);
        assertThat(output)
                .contains("[DEPRECATED CONFIG] studio.features.workspace.persistence=jdbc is deprecated")
                .contains("Use mybatis instead");
    }

    @Test
    void missingConfigurationUsesDefaultType() {
        EnvironmentPersistenceTypeResolver resolver = new EnvironmentPersistenceTypeResolver(new MockEnvironment());

        assertThat(resolver.resolve("workspace", StudioPersistenceType.MYBATIS))
                .isEqualTo(StudioPersistenceType.MYBATIS);
    }

    @Test
    void deprecatedDefaultTypeIsNormalized() {
        EnvironmentPersistenceTypeResolver resolver = new EnvironmentPersistenceTypeResolver(new MockEnvironment());

        assertThat(resolver.resolve("workspace", StudioPersistenceType.JDBC))
                .isEqualTo(StudioPersistenceType.MYBATIS);
    }

    @Test
    void invalidValueFailsFast() {
        EnvironmentPersistenceTypeResolver resolver = new EnvironmentPersistenceTypeResolver(
                new MockEnvironment().withProperty("studio.features.workspace.persistence", "unknown"));

        assertThatThrownBy(() -> resolver.resolve("workspace", StudioPersistenceType.JPA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("studio.features.workspace.persistence")
                .hasMessageContaining("jpa, mybatis, jdbc");
    }
}
