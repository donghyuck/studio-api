package studio.one.application.template.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import studio.one.platform.autoconfigure.PersistenceProperties;

class TemplateFeaturePropertiesTest {

    @Test
    void globalMyBatisFallsBackToDirectJdbcWhenFeaturePersistenceIsUnset() {
        TemplateFeatureProperties properties = new TemplateFeatureProperties();

        assertEquals(PersistenceProperties.Type.jdbc,
                properties.resolvePersistence(PersistenceProperties.Type.mybatis));
    }

    @Test
    void explicitFeaturePersistenceIsNotRewritten() {
        TemplateFeatureProperties properties = new TemplateFeatureProperties();
        properties.setPersistence(PersistenceProperties.Type.mybatis);

        assertEquals(PersistenceProperties.Type.mybatis,
                properties.resolvePersistence(PersistenceProperties.Type.jpa));
    }
}
