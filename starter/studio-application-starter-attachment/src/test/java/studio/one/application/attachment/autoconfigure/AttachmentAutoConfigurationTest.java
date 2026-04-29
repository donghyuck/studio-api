package studio.one.application.attachment.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.persistence.AttachmentRepository;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.attachment.storage.FileStorage;
import studio.one.application.attachment.storage.LocalFileStore;
import studio.one.application.attachment.thumbnail.LocalThumbnailStore;
import studio.one.application.attachment.thumbnail.ThumbnailKey;
import studio.one.application.attachment.thumbnail.ThumbnailService;
import studio.one.application.attachment.thumbnail.ThumbnailStorage;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.thumbnail.ThumbnailGenerationService;
import studio.one.platform.thumbnail.autoconfigure.ThumbnailAutoConfiguration;

@ExtendWith(OutputCaptureExtension.class)
class AttachmentAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    ThumbnailAutoConfiguration.class,
                    AttachmentAutoConfiguration.class))
            .withBean(AttachmentRepository.class, AttachmentAutoConfigurationTest::attachmentRepository)
            .withPropertyValues(
                    "studio.features.attachment.enabled=true",
                    "studio.features.attachment.persistence=jdbc");

    @BeforeEach
    void resetMigrationWarnings() {
        @SuppressWarnings("unchecked")
        Set<String> warned = (Set<String>) ReflectionTestUtils.getField(ConfigurationPropertyMigration.class, "WARNED");
        assertThat(warned).isNotNull();
        warned.clear();
    }

    @Test
    void usesTargetRuntimeStorageAndThumbnailProperties(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.attachment.storage.base-dir=/target-storage",
                        "studio.attachment.storage.ensure-dirs=false",
                        "studio.attachment.thumbnail.base-dir=/target-thumbnail",
                        "studio.attachment.thumbnail.ensure-dirs=false",
                        "studio.thumbnail.default-size=256")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    assertThat(context.getBean(FileStorage.class)).isInstanceOf(LocalFileStore.class);
                    assertThat(baseDir(context.getBean(FileStorage.class))).isEqualTo("/target-storage");

                    assertThat(context.getBean(ThumbnailStorage.class)).isInstanceOf(LocalThumbnailStore.class);
                    assertThat(baseDir(context.getBean(ThumbnailStorage.class))).isEqualTo("/target-thumbnail");
                    assertThat(defaultSize(context.getBean(ThumbnailGenerationService.class))).isEqualTo(256);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void usesLegacyRuntimePropertiesWhenTargetsAreMissingAndWarns(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.features.attachment.storage.base-dir=/legacy-storage",
                        "studio.features.attachment.storage.ensure-dirs=false",
                        "studio.features.attachment.thumbnail.base-dir=/legacy-thumbnail",
                        "studio.features.attachment.thumbnail.ensure-dirs=false",
                        "studio.features.attachment.thumbnail.default-size=96")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    assertThat(baseDir(context.getBean(FileStorage.class))).isEqualTo("/legacy-storage");
                    assertThat(baseDir(context.getBean(ThumbnailStorage.class))).isEqualTo("/legacy-thumbnail");
                    assertThat(defaultSize(context.getBean(ThumbnailGenerationService.class))).isEqualTo(96);
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.features.attachment.storage.base-dir is deprecated")
                            .contains("Use studio.attachment.storage.base-dir instead")
                            .contains("[DEPRECATED CONFIG] studio.features.attachment.thumbnail.base-dir is deprecated")
                            .contains("Use studio.attachment.thumbnail.base-dir instead")
                            .contains("[DEPRECATED CONFIG] studio.features.attachment.thumbnail.default-size is deprecated")
                            .contains("Use studio.thumbnail.default-size instead");
                });
    }

    @Test
    void targetRuntimePropertiesWinOverLegacyPerSubnamespace(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.attachment.storage.base-dir=/target-storage",
                        "studio.attachment.storage.ensure-dirs=false",
                        "studio.features.attachment.storage.base-dir=/legacy-storage",
                        "studio.features.attachment.storage.ensure-dirs=true",
                        "studio.attachment.thumbnail.base-dir=/target-thumbnail",
                        "studio.attachment.thumbnail.enabled=true",
                        "studio.attachment.thumbnail.ensure-dirs=false",
                        "studio.thumbnail.default-size=256",
                        "studio.features.attachment.thumbnail.enabled=false",
                        "studio.features.attachment.thumbnail.base-dir=/legacy-thumbnail",
                        "studio.features.attachment.thumbnail.default-size=96")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    assertThat(baseDir(context.getBean(FileStorage.class))).isEqualTo("/target-storage");
                    assertThat(baseDir(context.getBean(ThumbnailStorage.class))).isEqualTo("/target-thumbnail");
                    assertThat(defaultSize(context.getBean(ThumbnailGenerationService.class))).isEqualTo(256);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void resolvesStorageAndThumbnailSubnamespacesIndependently(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.attachment.storage.base-dir=/target-storage",
                        "studio.attachment.storage.ensure-dirs=false",
                        "studio.features.attachment.storage.base-dir=/legacy-storage",
                        "studio.features.attachment.thumbnail.base-dir=/legacy-thumbnail",
                        "studio.features.attachment.thumbnail.ensure-dirs=false",
                        "studio.features.attachment.thumbnail.default-size=80")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    assertThat(baseDir(context.getBean(FileStorage.class))).isEqualTo("/target-storage");
                    assertThat(baseDir(context.getBean(ThumbnailStorage.class))).isEqualTo("/legacy-thumbnail");
                    assertThat(defaultSize(context.getBean(ThumbnailGenerationService.class))).isEqualTo(80);
                    assertThat(output)
                            .doesNotContain("[DEPRECATED CONFIG] studio.features.attachment.storage.* is deprecated")
                            .contains("[DEPRECATED CONFIG] studio.features.attachment.thumbnail.base-dir is deprecated")
                            .contains("[DEPRECATED CONFIG] studio.features.attachment.thumbnail.default-size is deprecated")
                            .contains("Use studio.thumbnail.default-size instead");
                });
    }

    @Test
    void missingTargetThumbnailEnabledFallsBackToLegacyDisabled(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.attachment.thumbnail.base-dir=/target-thumbnail",
                        "studio.features.attachment.thumbnail.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ThumbnailStorage.class);
                    assertThat(context).doesNotHaveBean(ThumbnailService.class);
                    assertThat(output)
                            .contains("[DEPRECATED CONFIG] studio.features.attachment.thumbnail.enabled is deprecated")
                            .contains("Use studio.attachment.thumbnail.enabled instead");
                });
    }

    @Test
    void targetThumbnailDisabledWinsOverLegacyEnabled(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "studio.attachment.storage.ensure-dirs=false",
                        "studio.attachment.thumbnail.enabled=false",
                        "studio.features.attachment.thumbnail.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ThumbnailStorage.class);
                    assertThat(context).doesNotHaveBean(ThumbnailService.class);
                    assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
                });
    }

    @Test
    void preservesCustomStorageBeans() {
        FileStorage fileStorage = new TestFileStorage();
        ThumbnailStorage thumbnailStorage = new TestThumbnailStorage();

        contextRunner
                .withBean(FileStorage.class, () -> fileStorage)
                .withBean(ThumbnailStorage.class, () -> thumbnailStorage)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FileStorage.class);
                    assertThat(context).hasSingleBean(ThumbnailStorage.class);
                    assertThat(context.getBean(FileStorage.class)).isSameAs(fileStorage);
                    assertThat(context.getBean(ThumbnailStorage.class)).isSameAs(thumbnailStorage);
                    assertThat(context).doesNotHaveBean(LocalFileStore.class);
                    assertThat(context).doesNotHaveBean(LocalThumbnailStore.class);
                    assertThat(context).hasSingleBean(AttachmentService.class);
                    assertThat(context).hasSingleBean(ThumbnailService.class);
                });
    }

    private static String baseDir(Object bean) {
        return (String) ReflectionTestUtils.getField(bean, "baseDir");
    }

    private static int defaultSize(ThumbnailGenerationService service) {
        return service.generationOptions().defaultSize();
    }

    private static AttachmentRepository attachmentRepository() {
        return (AttachmentRepository) Proxy.newProxyInstance(
                AttachmentRepository.class.getClassLoader(),
                new Class<?>[] { AttachmentRepository.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "AttachmentRepositoryStub";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == Optional.class) {
                        return Optional.empty();
                    }
                    if (returnType == List.class) {
                        return List.of();
                    }
                    if (returnType == Page.class) {
                        return Page.empty();
                    }
                    if (returnType == Void.TYPE) {
                        return null;
                    }
                    if (returnType == String.class) {
                        return "";
                    }
                    return null;
                });
    }

    private static class TestFileStorage implements FileStorage {

        @Override
        public String save(Attachment attachment, InputStream input) {
            return "memory";
        }

        @Override
        public InputStream load(Attachment attachment) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(Attachment attachment) {
        }
    }

    private static class TestThumbnailStorage implements ThumbnailStorage {

        @Override
        public String save(ThumbnailKey key, InputStream input) {
            return "memory";
        }

        @Override
        public InputStream load(ThumbnailKey key) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(ThumbnailKey key) {
        }

        @Override
        public void deleteAll(int objectType, long attachmentId) {
        }
    }
}
