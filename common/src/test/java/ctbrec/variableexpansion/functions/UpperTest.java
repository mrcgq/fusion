package ctbrec.variableexpansion.functions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpperTest {
    Upper upper = new Upper();

    @Test
    void testNullParams() {
        assertEquals("", upper.apply((Object[]) null));
    }

    @Test
    void testEmptyParams() {
        assertEquals("", upper.apply());
    }

    @Test
    void testNormalStrings() {
        assertEquals("HELLO WORLD", upper.apply("hello world"));
        assertEquals("HELLO WORLD", upper.apply("hElLo woRlD"));
        assertEquals("HELLO WORLD", upper.apply("HELLO WORLD"));
    }

    @Test
    void testSpecialCharacters() {
        assertEquals("HE{LLO\nWO$RLD", upper.apply("he{LLo\nwo$rlD"));
    }
}
