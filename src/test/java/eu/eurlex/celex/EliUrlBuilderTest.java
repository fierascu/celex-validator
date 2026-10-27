package eu.eurlex.celex;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class EliUrlBuilderTest {

    @Inject
    CelexParser parser;

    @Inject
    EliUrlBuilder builder;

    private String url(String celex) {
        return parser.parse(celex)
                .map(builder::build)
                .orElseThrow(() -> new AssertionError("Not parseable: " + celex));
    }

    // ── ELI URLs for secondary legislation ──────────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("ELI URLs for Sector 3 legislation")
    @CsvSource({
            // Regulation: leading zeros stripped in number
            "32023R2854, https://eur-lex.europa.eu/eli/reg/2023/2854/oj/eng",
            "32006L0112, https://eur-lex.europa.eu/eli/dir/2006/112/oj/eng",
            "32016R0679, https://eur-lex.europa.eu/eli/reg/2016/679/oj/eng",
            "32013D0755, https://eur-lex.europa.eu/eli/dec/2013/755/oj/eng",
            // Recommendation
            "32003H0361, https://eur-lex.europa.eu/eli/reco/2003/361/oj/eng",
            // Implementing regulation
            "32011X0001, https://eur-lex.europa.eu/eli/reg_impl/2011/1/oj/eng",
            // Delegated regulation
            "32014Y0001, https://eur-lex.europa.eu/eli/reg_del/2014/1/oj/eng",
    })
    void eliUrls(String celex, String expected) {
        assertEquals(expected, url(celex));
    }

    // ── CELEX fallback for non-legislation ──────────────────────────────────

    @ParameterizedTest(name = "{0} → CELEX fallback")
    @DisplayName("CELEX fallback URL for case law and preparatory acts")
    @CsvSource({
            "62019CJ0001, https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:62019CJ0001",
            "52023PC0206, https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:52023PC0206",
            "12002E/TXT,  https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:12002E/TXT",
            "22013A0907(01), https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:22013A0907(01)",
    })
    void celexFallbackUrls(String celex, String expected) {
        assertEquals(expected.strip(), url(celex.strip()));
    }
}
