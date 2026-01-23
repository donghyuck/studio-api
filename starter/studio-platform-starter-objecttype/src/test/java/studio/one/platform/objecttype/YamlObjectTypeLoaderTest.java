package studio.one.platform.objecttype;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

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
}
