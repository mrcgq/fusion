package ctbrec.variableexpansion.functions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrimTest {
    Trim trim = new Trim();

    @Test
    void testNullParams() {
        assertEquals("", trim.apply((Object[]) null));
    }

    @Test
    void testEmptyParams() {
        assertEquals("", trim.apply());
    }

    @Test
    void testNormalStrings() {
        assertEquals("hello", trim.apply("hello"));
        assertEquals("hello", trim.apply(" hello "));
        assertEquals("hello", trim.apply("   hello   "));
        assertEquals("hello", trim.apply("\thello\t"));
        assertEquals("hello", trim.apply("\nhello\n"));
    }

    @Test
    void testSpecialCharacters() {
        assertEquals("he{LLo\nwo $rlD", trim.apply("he{LLo\nwo $rlD"));
    }
}
