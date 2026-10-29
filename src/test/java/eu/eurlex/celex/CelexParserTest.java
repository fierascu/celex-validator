package eu.eurlex.celex;

import eu.eurlex.celex.model.CelexComponents;
import eu.eurlex.celex.model.Sector;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CelexParserTest {

    @Inject
    CelexParser parser;

    // -------------------------------------------------------------------------
    // Sector 3 – Secondary legislation (most common)
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "valid legislation: {0}")
    @CsvSource({
            "32006L0112, 3, 2006, L, 0112",
            "32023R2854, 3, 2023, R, 2854",
            "32016R0679, 3, 2016, R, 0679",   // GDPR
            "32009L0028, 3, 2009, L, 0028",
            "32013D0755, 3, 2013, D, 0755",
    })
    void sector3Valid(String celex, String expectedSector, String expectedYear,
                      String expectedType, String expectedNumber) {
        Optional<CelexComponents> result = parser.parse(celex);
        assertTrue(result.isPresent(), "Expected valid: " + celex);
        CelexComponents c = result.get();
        assertEquals(expectedSector, c.sector().code);
        assertEquals(expectedYear, c.year());
        assertEquals(expectedType, c.documentType().code);
        assertEquals(expectedNumber, c.number());
        assertNull(c.suffix());
    }

    @Test
    @DisplayName("Regulation with /TXT suffix")
    void sector3WithSuffix() {
        Optional<CelexComponents> result = parser.parse("32023R2854/TXT");
        assertTrue(result.isPresent());
        assertEquals("/TXT", result.get().suffix());
        assertEquals("2854", result.get().number());
    }

    @Test
    @DisplayName("Delegated regulation (Y type code)")
    void sector3DelegatedRegulation() {
        Optional<CelexComponents> result = parser.parse("32014Y0527(02)");
        assertTrue(result.isPresent());
        assertEquals("(02)", result.get().suffix());
    }

    // -------------------------------------------------------------------------
    // Sector 6 – Case law
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "valid case law: {0}")
    @CsvSource({
            "62019CJ0001, 6, 2019, CJ, 0001",
            "61990CJ0213, 6, 1990, CJ, 0213",
            "62018TJ0001, 6, 2018, TJ, 0001",
            "62020CO0001, 6, 2020, CO, 0001",
    })
    void sector6Valid(String celex, String expectedSector, String expectedYear,
                      String expectedType, String expectedNumber) {
        Optional<CelexComponents> result = parser.parse(celex);
        assertTrue(result.isPresent(), "Expected valid: " + celex);
        CelexComponents c = result.get();
        assertEquals(expectedSector, c.sector().code);
        assertEquals(expectedYear, c.year());
        assertEquals(expectedType, c.documentType().code);
        assertEquals(expectedNumber, c.number());
    }

    // -------------------------------------------------------------------------
    // Sector 5 – Preparatory acts
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "valid preparatory act: {0}")
    @CsvSource({
            "52023PC0206, 5, 2023, PC, 0206",
            "52020DC0381, 5, 2020, DC, 0381",
    })
    void sector5Valid(String celex, String expectedSector, String expectedYear,
                      String expectedType, String expectedNumber) {
        Optional<CelexComponents> result = parser.parse(celex);
        assertTrue(result.isPresent(), "Expected valid: " + celex);
        CelexComponents c = result.get();
        assertEquals(expectedSector, c.sector().code);
        assertEquals(expectedYear, c.year());
        assertEquals(expectedNumber, c.number());
    }

    // -------------------------------------------------------------------------
    // Sector 1 – Treaties
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Treaty article with year prefix")
    void sector1WithYear() {
        Optional<CelexComponents> result = parser.parse("12002E/TXT");
        assertTrue(result.isPresent());
        assertEquals(Sector.TREATIES, result.get().sector());
        assertEquals("2002", result.get().year());
        assertEquals("/TXT", result.get().suffix());
    }

    @Test
    @DisplayName("Treaty article without year prefix")
    void sector1WithoutYear() {
        Optional<CelexComponents> result = parser.parse("11957E012");
        assertTrue(result.isPresent());
        assertEquals(Sector.TREATIES, result.get().sector());
    }

    // -------------------------------------------------------------------------
    // Sector 2 – External agreements
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("External agreement")
    void sector2Valid() {
        Optional<CelexComponents> result = parser.parse("22013A0907(01)");
        assertTrue(result.isPresent());
        assertEquals("2", result.get().sector().code);
        assertEquals("2013", result.get().year());
        assertEquals("(01)", result.get().suffix());
    }

    // -------------------------------------------------------------------------
    // Invalid inputs
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "invalid: {0}")
    @ValueSource(strings = {
            "",
            "   ",
            "ABCDEF",
            "99999XX9999",
            "3ABCDL0001",          // non-year after sector
            "32006Z9999",          // Z is not a valid legislation type
            // Injection attempts: metacharacters must never be accepted
            "72006<img src=x onerror=alert(1)>",
            "C2006<script>",
            "72006\"onmouseover=\"x",
            "72006> } UNION { ?s ?p ?o",
            "72006ab.c -x",
            "72006abc\ndef",
            "1<b>/TXT",
            "32006L0112&x=1",
    })
    void invalidInputs(String celex) {
        Optional<CelexComponents> result = parser.parse(celex);
        assertFalse(result.isPresent(), "Expected invalid: '" + celex + "'");
    }

    @Test
    @DisplayName("National implementation measure with underscore is valid")
    void sector7WithUnderscore() {
        Optional<CelexComponents> result = parser.parse("72014L0065DEU_202001398");
        assertTrue(result.isPresent());
        assertEquals("7", result.get().sector().code);
        assertEquals("L0065DEU_202001398", result.get().number());
    }

    @Test
    @DisplayName("Overly long input is rejected")
    void tooLongInput() {
        assertFalse(parser.parse("32006L" + "1".repeat(100)).isPresent());
    }

    @Test
    @DisplayName("null input returns empty")
    void nullInput() {
        assertFalse(parser.parse(null).isPresent());
    }

    @Test
    @DisplayName("leading/trailing whitespace is tolerated")
    void whitespaceInput() {
        assertTrue(parser.parse("  32006L0112  ").isPresent());
    }
}
