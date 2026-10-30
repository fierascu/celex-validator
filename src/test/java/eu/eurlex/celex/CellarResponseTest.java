package eu.eurlex.celex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static eu.eurlex.celex.UrlCheckResource.CellarClient.parseAskResponse;
import static org.junit.jupiter.api.Assertions.*;

class CellarResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("ASK true means found")
    void askTrue() throws IOException {
        assertTrue(parseAskResponse(mapper, 200, "{ \"head\": { \"link\": [] }, \"boolean\": true }"));
    }

    @Test
    @DisplayName("ASK false means not found")
    void askFalse() throws IOException {
        assertFalse(parseAskResponse(mapper, 200, "{\"head\":{},\"boolean\":false}"));
    }

    @ParameterizedTest(name = "HTTP {0} is an error, not 'not found'")
    @ValueSource(ints = {400, 404, 500, 503})
    void nonSuccessStatusThrows(int status) {
        assertThrows(IOException.class, () -> parseAskResponse(mapper, status, "Bad request"));
    }

    @Test
    @DisplayName("Body without a boolean result is an error")
    void missingBooleanThrows() {
        assertThrows(IOException.class, () -> parseAskResponse(mapper, 200, "{\"head\":{}}"));
        assertThrows(IOException.class, () -> parseAskResponse(mapper, 200, "{\"boolean\":\"true\"}"));
    }

    @Test
    @DisplayName("Non-JSON body is an error")
    void htmlBodyThrows() {
        assertThrows(IOException.class, () -> parseAskResponse(mapper, 200, "<html>maintenance</html>"));
    }
}
