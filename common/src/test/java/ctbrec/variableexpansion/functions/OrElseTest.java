package ctbrec.variableexpansion.functions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrElseTest {
    OrElse orElse = new OrElse();

    @Test
    void testNullParams() {
        assertEquals("", orElse.apply((Object[]) null));
    }

    @Test
    void testEmptyParams() {
        assertEquals("", orElse.apply());
    }

    @Test
    void testHappyPath() {
        assertEquals("world", orElse.apply("world", "hello"));
    }

    @Test
    void testFallback() {
        assertEquals("hello", orElse.apply("asdf ${variable} asdf", "hello"));
    }
}
