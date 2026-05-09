package studio.one.platform.objecttype;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.junit.jupiter.api.Assertions.*;

import studio.one.platform.autoconfigure.objecttype.YamlObjectTypeLoader;

public class YamlObjectTypeLoaderTest {

    @Test
    void loadParsesObjecttypes() {
        YamlObjectTypeLoader loader = new YamlObjectTypeLoader(new DefaultResourceLoader());
        YamlObjectTypeLoader.Result result = loader.load("classpath:objecttype-test.yml");

        assertEquals(1, result.byType().size());
        assertTrue(result.byType().containsKey(1001));
        assertTrue(result.byKey().containsKey("document"));
        assertTrue(result.policies().containsKey(1001));
    }

    @Test
    void malformedYamlFailsFast() {
        YamlObjectTypeLoader loader = new YamlObjectTypeLoader(new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource("objecttypes:\n  - [broken".getBytes()) {
                    @Override
                    public boolean exists() {
                        return true;
                    }
                };
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        });

        assertThrows(RuntimeException.class, () -> loader.load("classpath:broken-objecttype.yml"));
    }
}
