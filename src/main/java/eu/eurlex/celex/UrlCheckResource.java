package eu.eurlex.celex;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks whether a CELEX number is actually published in the EU CELLAR
 * linked-data repository, using the public SPARQL endpoint at
 * {@code https://publications.europa.eu/webapi/rdf/sparql}.
 *
 * <p>The HTML EUR-Lex pages are protected by AWS WAF and always return an
 * empty body to plain HTTP clients, so scraping the page is not feasible.
 * The CELLAR SPARQL endpoint is public, returns JSON, and reliably indicates
 * whether a document exists.
 *
 * <p>Endpoint: {@code GET /api/check-url?url=https://eur-lex.europa.eu/…}
 *
 * <p>The {@code url} parameter is accepted for API compatibility but only the
 * CELEX number embedded in it is used; the actual check goes to CELLAR.
 *
 * <p>Response JSON:
 * <pre>
 *   { "found": true  }   – CELEX exists in CELLAR
 *   { "found": false }   – CELEX not found in CELLAR
 *   { "found": false, "error": "…" }  – could not reach CELLAR
 * </pre>
 */
@Path("/api/check-url")
@Produces(MediaType.APPLICATION_JSON)
public class UrlCheckResource {

    private static final Logger LOG = Logger.getLogger(UrlCheckResource.class);

    private static final String SPARQL_ENDPOINT =
            "https://publications.europa.eu/webapi/rdf/sparql";
    private static final String CELLAR_CELEX_BASE =
            "http://publications.europa.eu/resource/celex/";

    /**
     * Allowlist for characters permitted in a CELEX number that is interpolated
     * into the SPARQL query. Digits, uppercase letters, forward slash, and
     * parentheses cover every valid CELEX format including suffixes.
     */
    private static final Pattern CELEX_SAFE = Pattern.compile("[A-Z0-9/(). -]{1,64}", Pattern.CASE_INSENSITIVE);

    private static final int MAX_INPUT_LENGTH = 256;

    @Inject
    CelexParser parser;

    @Inject
    CellarClient cellarClient;

    @GET
    @Blocking
    public Map<String, Object> checkUrl(@QueryParam("url") String url,
                                        @QueryParam("celex") String celex) {
        // Prefer the explicit celex param; fall back to extracting it from the url value
        // (handles legal-content URLs that embed ?uri=CELEX:XXXXX)
        if (celex == null || celex.isBlank()) {
            if (url != null && url.length() > MAX_INPUT_LENGTH) {
                return Map.of("found", false, "error", "Input too long");
            }
            celex = extractCelexFromUrl(url);
        }
        if (celex == null || celex.isBlank()) {
            return Map.of("found", false, "error", "No CELEX number provided");
        }
        if (celex.length() > 64) {
            return Map.of("found", false, "error", "Input too long");
        }

        // Reject anything that isn't a syntactically valid CELEX number before it is
        // interpolated into the SPARQL query string sent to CELLAR.
        if (parser.parse(celex).isEmpty()) {
            return Map.of("found", false, "error", "'" + celex + "' is not a recognised CELEX number");
        }

        // Secondary allowlist check: ensure only safe characters reach the query.
        if (!CELEX_SAFE.matcher(celex).matches()) {
            return Map.of("found", false, "error", "Invalid characters in CELEX number");
        }

        try {
            return cellarClient.query(celex);
        } catch (Exception e) {
            LOG.warnf("CELLAR check unavailable for celex=%s", celex);
            return Map.of("found", false, "error", "CELLAR service is currently unavailable");
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Extracts the CELEX identifier embedded inside a legal-content EUR-Lex URL.
     * e.g. {@code …/legal-content/EN/TXT/?uri=CELEX:32006L0112} → {@code 32006L0112}
     */
    private static String extractCelexFromUrl(String url) {
        if (url == null) return null;
        int idx = url.indexOf("uri=CELEX:");
        if (idx >= 0) {
            String rest = url.substring(idx + "uri=CELEX:".length());
            int amp = rest.indexOf('&');
            return amp >= 0 ? rest.substring(0, amp) : rest;
        }
        return null;
    }

    // -------------------------------------------------------------------------

    /**
     * CDI bean that owns the {@link HttpClient} and wraps the outbound SPARQL
     * call with fault-tolerance: 10 s timeout, 2 retries, circuit breaker.
     */
    @ApplicationScoped
    static class CellarClient {

        private static final Logger LOG = Logger.getLogger(CellarClient.class);

        private final HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        @Timeout(value = 10_000)          // ms — overall method timeout
        @Retry(maxRetries = 2, delay = 300)
        @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 15_000)
        Map<String, Object> query(String celex) {
            String sparql = "ASK { ?s <http://www.w3.org/2002/07/owl#sameAs>"
                    + " <" + CELLAR_CELEX_BASE + celex + "> }";
            String encoded = URLEncoder.encode(sparql, StandardCharsets.UTF_8);
            URI uri = URI.create(SPARQL_ENDPOINT + "?query=" + encoded);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/sparql-results+json")
                        .GET()
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

                // Response body: { "head": { "link": [] }, "boolean": true }
                String body = response.body();
                boolean found = body.contains("\"boolean\": true")
                             || body.contains("\"boolean\":true");
                return Map.of("found", found);

            } catch (Exception e) {
                LOG.errorf(e, "CELLAR SPARQL request failed for celex=%s", celex);
                throw new RuntimeException("CELLAR request failed", e);
            }
        }
    }
}
