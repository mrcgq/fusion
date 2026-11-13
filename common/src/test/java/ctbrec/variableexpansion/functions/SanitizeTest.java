package ctbrec.variableexpansion.functions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SanitizeTest {
    Sanitize sanitize = new Sanitize();

    @Test
    void testNullParams() {
        assertEquals("", sanitize.apply((Object[]) null));
    }

    @Test
    void testEmptyParams() {
        assertEquals("", sanitize.apply());
    }

    @Test
    void testNormalStrings() {
        assertEquals("hel!lo", sanitize.apply("hel!lo"));
        assertEquals("hE$lLo", sanitize.apply("hE$lLo"));
        assertEquals("HELL%O", sanitize.apply("HELL%O"));
    }

    @Test
    void testSpecialCharacters() {
        assertEquals("asdf_asdf", sanitize.apply("asdf asdf"));
        assertEquals("asdf_asdf", sanitize.apply("asdf\"asdf"));
        assertEquals("asdf_asdf", sanitize.apply("asdf'asdf"));
        assertEquals("asdf_asdf", sanitize.apply("asdf\\asdf"));
        assertEquals("asdf_asdf", sanitize.apply("asdf/asdf"));
    }
}
