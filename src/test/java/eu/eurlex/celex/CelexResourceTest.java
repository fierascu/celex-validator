package eu.eurlex.celex;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("Missing celex parameter returns valid=false")
    void missingParamReturnsError() {
        given()
                .when().get("/api/validate")
                .then()
                .statusCode(200)
                .body("valid", is(false));
    }
}
