package eu.eurlex.celex;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

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

    private static final String SPARQL_ENDPOINT =
            "https://publications.europa.eu/webapi/rdf/sparql";
    private static final String CELLAR_CELEX_BASE =
            "http://publications.europa.eu/resource/celex/";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    @GET
    public Map<String, Object> checkUrl(@QueryParam("url") String url,
                                        @QueryParam("celex") String celex) {
        // Prefer the explicit celex param; fall back to extracting it from the url value
        // (handles legal-content URLs that embed ?uri=CELEX:XXXXX)
        if (celex == null || celex.isBlank()) {
            celex = extractCelexFromUrl(url);
        }
        if (celex == null || celex.isBlank()) {
            return Map.of("found", false, "error", "No CELEX number provided");
        }

        return querycellar(celex);
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

    private Map<String, Object> querycellar(String celex) {
        // ASK query: is this CELEX URI the target of an owl:sameAs in CELLAR?
        String sparql = "ASK { ?s <http://www.w3.org/2002/07/owl#sameAs>"
                + " <" + CELLAR_CELEX_BASE + celex + "> }";
        String query = URLEncoder.encode(sparql, StandardCharsets.UTF_8);
        URI uri = URI.create(SPARQL_ENDPOINT + "?query=" + query);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(TIMEOUT)
                    .header("Accept", "application/sparql-results+json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            // Response body: {  "head": { "link": [] }, "boolean": true}
            // Use contains() for both spaced and compact forms.
            String body = response.body();
            boolean found = body.contains("\"boolean\": true")
                         || body.contains("\"boolean\":true");
            return Map.of("found", found);

        } catch (Exception e) {
            return Map.of("found", false, "error", "Could not reach CELLAR: " + e.getMessage());
        }
    }
}
