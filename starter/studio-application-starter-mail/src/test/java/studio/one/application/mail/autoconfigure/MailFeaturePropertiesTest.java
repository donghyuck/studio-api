package studio.one.application.mail.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import studio.one.platform.autoconfigure.PersistenceProperties;

class MailFeaturePropertiesTest {

    @Test
    void featurePropertyResolverLeavesGlobalMyBatisRaw() {
        MailFeatureProperties properties = new MailFeatureProperties();

        assertEquals(PersistenceProperties.Type.mybatis,
                properties.resolvePersistence(PersistenceProperties.Type.mybatis));
    }

    @Test
    void featurePropertyResolverLeavesExplicitMyBatisRaw() {
        MailFeatureProperties properties = new MailFeatureProperties();
        properties.setPersistence(PersistenceProperties.Type.mybatis);

        assertEquals(PersistenceProperties.Type.mybatis,
                properties.resolvePersistence(PersistenceProperties.Type.jpa));
    }
}
