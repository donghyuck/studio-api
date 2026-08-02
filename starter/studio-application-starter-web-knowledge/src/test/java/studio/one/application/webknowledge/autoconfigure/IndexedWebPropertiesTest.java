package studio.one.application.webknowledge.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class IndexedWebPropertiesTest {

    @Test
    void onlyHttpsSchemeCanBeConfigured() {
        IndexedWebProperties properties = new IndexedWebProperties();

        properties.getFetch().setAllowedSchemes(List.of("HTTPS"));
        assertEquals(List.of("https"), properties.getFetch().getAllowedSchemes());
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.getFetch().setAllowedSchemes(List.of("http", "https")));
    }

    @Test
    void piiRedactionIsEnabledByDefault() {
        IndexedWebProperties properties = new IndexedWebProperties();

        assertEquals(true, properties.getContentSecurity().isPiiRedactionEnabled());
    }
}
