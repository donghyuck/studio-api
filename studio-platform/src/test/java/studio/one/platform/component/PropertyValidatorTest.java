package studio.one.platform.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PropertyValidatorTest {

    @Test
    void detectsNestedSensitivePropertyNames() {
        assertThat(PropertyValidator.isSensitiveProperty("spring.datasource.password")).isTrue();
        assertThat(PropertyValidator.isSensitiveProperty("server.ssl.key-store-password")).isTrue();
        assertThat(PropertyValidator.isSensitiveProperty("jasypt.encryptor.password")).isTrue();
        assertThat(PropertyValidator.isSensitiveProperty("spring.security.oauth2.client.registration.app.client-secret"))
                .isTrue();
    }

    @Test
    void ignoresNonSensitivePropertyNames() {
        assertThat(PropertyValidator.isSensitiveProperty("studio.features.text.enabled")).isFalse();
        assertThat(PropertyValidator.isSensitiveProperty("")).isFalse();
        assertThat(PropertyValidator.isSensitiveProperty(null)).isFalse();
    }
}
