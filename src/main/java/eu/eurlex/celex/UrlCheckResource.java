package eu.eurlex.celex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.cache.CacheResult;
import io.smallrye.faulttolerance.api.RateLimit;
import io.smallrye.faulttolerance.api.RateLimitException;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
     * into the SPARQL query. Digits, letters, underscore, forward slash, and
     * parentheses cover every valid CELEX format including suffixes.
     */
    private static final Pattern CELEX_SAFE = Pattern.compile("[A-Z0-9_/()]{1,64}", Pattern.CASE_INSENSITIVE);

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
            Map<String, Object> result = cellarClient.query(celex);
            boolean found = result != null && Boolean.TRUE.equals(result.get("found"));
            LOG.debugf("check-url: celex=%s found=%b", celex, found);
            return result;
        } catch (RateLimitException | BulkheadException e) {
            LOG.warnf("CELLAR check rejected (overloaded) for celex=%s", celex);
            return Map.of("found", false, "error", "Too many requests, please try again shortly");
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
     * call with a result cache and fault tolerance.
     *
     * <p>Worst case is two 3 s attempts plus a 200 ms delay, which stays inside
     * the frontend's 10 s timeout. The rate limit and bulkhead cap how much load
     * this service can put on CELLAR (and how many worker threads it can tie up);
     * per-client limits belong at the reverse proxy. Cache hits bypass all of it.
     */
    @ApplicationScoped
    static class CellarClient {

        private static final Logger LOG = Logger.getLogger(CellarClient.class);

        private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(3);

        private final HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(ATTEMPT_TIMEOUT)
                .build();

        @Inject
        ObjectMapper mapper;

        @CacheResult(cacheName = "cellar-exists")   // failures are not cached
        @RateLimit(value = 30, window = 1, windowUnit = ChronoUnit.SECONDS)
        @Bulkhead(10)
        @Retry(maxRetries = 1, delay = 200,
               abortOn = {RateLimitException.class, BulkheadException.class, CircuitBreakerOpenException.class})
        @Timeout(value = 3_000)           // ms — per attempt
        @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 15_000)
        Map<String, Object> query(String celex) {
            String sparql = "ASK { ?s <http://www.w3.org/2002/07/owl#sameAs>"
                    + " <" + CELLAR_CELEX_BASE + celex + "> }";
            String encoded = URLEncoder.encode(sparql, StandardCharsets.UTF_8);
            URI uri = URI.create(SPARQL_ENDPOINT + "?query=" + encoded);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(ATTEMPT_TIMEOUT)
                        .header("Accept", "application/sparql-results+json")
                        .GET()
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                return Map.of("found", parseAskResponse(mapper, response.statusCode(), response.body()));

            } catch (Exception e) {
                LOG.errorf(e, "CELLAR SPARQL request failed for celex=%s", celex);
                throw new RuntimeException("CELLAR request failed", e);
            }
        }

        /**
         * Interprets a SPARQL ASK response, e.g. {@code { "head": {}, "boolean": true }}.
         * A non-2xx status or a body without a boolean result is an error rather than
         * "not found", so outages surface to the client and count towards the circuit breaker.
         */
        static boolean parseAskResponse(ObjectMapper mapper, int status, String body) throws IOException {
            if (status < 200 || status >= 300) {
                throw new IOException("CELLAR returned HTTP " + status);
            }
            JsonNode result = mapper.readTree(body).get("boolean");
            if (result == null || !result.isBoolean()) {
                throw new IOException("CELLAR response has no boolean result");
            }
            return result.booleanValue();
        }
    }
}
