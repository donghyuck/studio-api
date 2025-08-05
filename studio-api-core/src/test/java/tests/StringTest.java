package tests;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringTest {

    @Test
    void testString() {
        String str = "Hello, World!";
        assertEquals("Hello, World!", str, "String comparison failed");
        assertEquals(13, str.length(), "String length is incorrect");
        assertTrue(str.contains("World"), "String does not contain 'World'");
    }
}
// This test class demonstrates basic string operations and assertions using JUnit 5.