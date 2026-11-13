package ctbrec.variableexpansion.functions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LowerTest {
    Lower lower = new Lower();

    @Test
    void testNullParams() {
        assertEquals("", lower.apply((Object[]) null));
    }

    @Test
    void testEmptyParams() {
        assertEquals("", lower.apply());
    }

    @Test
    void testNormalStrings() {
        assertEquals("hello world", lower.apply("hello world"));
        assertEquals("hello world", lower.apply("hElLo woRlD"));
        assertEquals("hello world", lower.apply("HELLO WORLD"));
    }

    @Test
    void testSpecialCharacters() {
        assertEquals("he{llo\nwo$rld", lower.apply("he{LLo\nwo$rlD"));
    }
}
