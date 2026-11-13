package ctbrec.variableexpansion.functions;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatTest {

    Format format = new Format();

    @Test
    void testNullParams() {
        assertEquals("", format.apply((Object[]) null));
    }

    @Test
    void testEmptyParams() {
        assertEquals("", format.apply());
    }

    @Test
    void testZonedDateTimeParameter() {
        String expected = "2023-02-26_17-11-23";
        String zdt = "2023-02-26T17:11:23.363270467+01:00[Europe/Berlin]";
        assertEquals(expected, format.apply(ZonedDateTime.parse(zdt)));
    }

    @Test
    void testStringParameter() {
        String expected = "2023-02-26_17-11-23";
        String zdt = "2023-02-26T17:11:23.363270467+01:00[Europe/Berlin]";
        assertEquals(expected, format.apply(zdt));
    }

    @Test
    void testInvalidStringParameter() {
        assertEquals("", format.apply("bullshit"));
    }

    @Test
    void testIStringParameterWithPattern() {
        String expected = "20230226-171123";
        String zdt = "2023-02-26T17:11:23.363270467+01:00[Europe/Berlin]";
        assertEquals(expected, format.apply(zdt, "yyyyMMdd-HHmmss"));
    }

    @Test
    void testIStringParameterWithInvalidPattern() {
        String expected = "";
        String zdt = "2023-02-26T17:11:23.363270467+01:00[Europe/Berlin]";
        assertEquals(expected, format.apply(zdt, "asdft"));
    }
}
