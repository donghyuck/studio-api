package studio.one.platform.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;

import studio.one.platform.component.event.PropertiesRefreshedEvent;
import studio.one.platform.service.ApplicationProperties;
import studio.one.platform.service.I18n;

class RepositoryImplTest {

    @TempDir
    File tempDir;

    @Test
    void getFileRejectsPathTraversal() {
        RepositoryImpl repository = createRepository();

        assertThatThrownBy(() -> repository.getFile("../outside.txt"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void getFileAllowsRootDirectory() throws IOException {
        RepositoryImpl repository = createRepository();

        File result = repository.getFile(".");

        assertThat(result.getCanonicalFile()).isEqualTo(tempDir.getCanonicalFile());
    }

    @Test
    void getFileReturnsCanonicalFileInsideRoot() throws IOException {
        RepositoryImpl repository = createRepository();
        File nested = new File(tempDir, "config/app.yml");
        Files.createDirectories(nested.getParentFile().toPath());
        Files.writeString(nested.toPath(), "ok");

        File result = repository.getFile("config/../config/app.yml");

        assertThat(result.getCanonicalFile()).isEqualTo(nested.getCanonicalFile());
    }

    @Test
    void startupRefreshEventDoesNotThrowWhenInitialized() {
        RepositoryImpl repository = createRepository();

        assertThatNoException()
                .isThrownBy(() -> repository.handlePropertiesRefreshedEvent(
                        new PropertiesRefreshedEvent(this, "startup")));
    }

    private RepositoryImpl createRepository() {
        I18n i18n = (code, args, locale) -> code + ":" + Locale.ROOT;
        RepositoryImpl repository = new RepositoryImpl(
                new StubApplicationProperties(),
                i18n,
                new MockEnvironment(),
                event -> {
                });
        ReflectionTestUtils.setField(repository, "rootResource", new FileSystemResource(tempDir));
        ReflectionTestUtils.setField(repository, "initialized", new AtomicBoolean(true));
        return repository;
    }

    private static class StubApplicationProperties extends java.util.HashMap<String, String>
            implements ApplicationProperties {

        @Override
        public boolean getBooleanProperty(String name) {
            return Boolean.parseBoolean(get(name));
        }

        @Override
        public boolean getBooleanProperty(String name, boolean defaultValue) {
            return containsKey(name) ? Boolean.parseBoolean(get(name)) : defaultValue;
        }

        @Override
        public java.util.Collection<String> getChildrenNames(String name) {
            return java.util.List.of();
        }

        @Override
        public int getIntProperty(String name, int defaultValue) {
            return containsKey(name) ? Integer.parseInt(get(name)) : defaultValue;
        }

        @Override
        public long getLongProperty(String name, long defaultValue) {
            return containsKey(name) ? Long.parseLong(get(name)) : defaultValue;
        }

        @Override
        public java.util.Collection<String> getPropertyNames() {
            return keySet();
        }

        @Override
        public String getStringProperty(String name, String defaultValue) {
            return getOrDefault(name, defaultValue);
        }
    }
}
