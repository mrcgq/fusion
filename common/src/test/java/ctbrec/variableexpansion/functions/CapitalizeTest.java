package ctbrec.variableexpansion.functions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapitalizeTest {
    Capitalize capitalize = new Capitalize();

    @Test
    void testNullParams() {
        assertEquals("", capitalize.apply((Object[]) null));
    }

    @Test
    void testEmptyParams() {
        assertEquals("", capitalize.apply());
    }

    @Test
    void testNormalStrings() {
        assertEquals("Hello World", capitalize.apply("hello world"));
        assertEquals("HElLo WoRlD", capitalize.apply("hElLo woRlD"));
        assertEquals("HELLO WORLD", capitalize.apply("HELLO WORLD"));
    }

    @Test
    void testSpecialCharacters() {
        assertEquals("He{LLo Wo\n$rlD", capitalize.apply("he{LLo wo\n$rlD"));
    }
}
