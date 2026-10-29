package eu.eurlex.celex;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CelexResourceTest {

    // ── Publishing warning ────────────────────────────────────────────────────

    @Test
    @DisplayName("Current-year legislation CELEX gets a publishing warning")
    void currentYearLegislationHasWarning() {
        // 32026L0001 is syntactically valid but may not be published in EUR-Lex yet
        given()
                .queryParam("celex", "32026L0001")
                .when().get("/api/validate")
                .then()
                .statusCode(200)
                .body("valid", is(true))
                .body("warning", not(emptyOrNullString()));
    }

    @Test
    @DisplayName("Past-year legislation CELEX has no publishing warning")
    void pastYearLegislationHasNoWarning() {
        given()
                .queryParam("celex", "32006L0112")
                .when().get("/api/validate")
                .then()
                .statusCode(200)
                .body("valid", is(true))
                .body("warning", nullValue());
    }

    @Test
    @DisplayName("Invalid CELEX returns valid=false and error message")
    void invalidCelexReturnsError() {
        given()
                .queryParam("celex", "NOTACELEX")
                .when().get("/api/validate")
                .then()
                .statusCode(200)
                .body("valid", is(false))
                .body("error", not(emptyOrNullString()));
    }

    @Test
    @DisplayName("HTML in a CELEX number is rejected, never returned as valid")
    void htmlInjectionIsInvalid() {
        given()
                .queryParam("celex", "72006<img src=x onerror=alert(1)>")
                .when().get("/api/validate")
                .then()
                .statusCode(200)
                .body("valid", is(false))
                .body("eurLexUrl", nullValue());
    }

    @Test
    @DisplayName("SPARQL metacharacters never reach CELLAR")
    void sparqlInjectionIsRejected() {
        given()
                .queryParam("celex", "72006> } UNION { ?s ?p ?o")
                .when().get("/api/check-url")
                .then()
                .statusCode(200)
                .body("found", is(false))
                .body("error", not(emptyOrNullString()));
    }

    @ParameterizedTest(name = "security headers on {0}")
    @ValueSource(strings = {"/", "/app.js", "/api/sectors"})
    void securityHeadersPresent(String path) {
        given()
                .when().get(path)
                .then()
                .statusCode(200)
                .header("Content-Security-Policy", containsString("script-src 'self'"))
                .header("Content-Security-Policy", containsString("frame-ancestors 'none'"))
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("Referrer-Policy", "no-referrer");
    }

    @Test
    @DisplayName("Page contains no inline script or inline event handlers")
    void pageHasNoInlineScript() {
        String html = given().when().get("/").then().statusCode(200).extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(html.matches("(?s).*<script>.*"), "inline <script> block");
        org.junit.jupiter.api.Assertions.assertFalse(html.matches("(?s).*\\son[a-z]+=\".*"), "inline event handler");
    }

    @Test
    @DisplayName("Missing celex parameter returns valid=false")
    void missingParamReturnsError() {
        given()
                .when().get("/api/validate")
                .then()
                .statusCode(200)
                .body("valid", is(false));
    }
}
