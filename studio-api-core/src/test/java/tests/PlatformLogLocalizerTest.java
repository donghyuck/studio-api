package tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;
import studio.api.platform.i18n.PlatformLogLocalizer;
 
@Slf4j
class PlatformLogLocalizerTest {
    
    @Test
    void testSingletonInstance() {
        PlatformLogLocalizer instance1 = PlatformLogLocalizer.getInstance();
        PlatformLogLocalizer instance2 = PlatformLogLocalizer.getInstance();
        
        log.info(  instance1.format( PlatformLogLocalizer.MessageCode.COMPONENT_STATE .code(), "TestComponent", "STARTED" ));
        assertNotNull(instance1, "Instance should not be null");
        assertSame(instance1, instance2, "Instances should be the same (singleton)");
    }

    @Test
    void testMessageCodeEnumValues() {
        PlatformLogLocalizer.MessageCode code = PlatformLogLocalizer.MessageCode.COMPONENT_STATE;
        assertEquals("002001", code.code(), "Message code should match");
        assertEquals("002001", code.toString(), "toString should return code");
    }
}