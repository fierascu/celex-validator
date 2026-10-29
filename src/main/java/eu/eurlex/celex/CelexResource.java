package eu.eurlex.celex;

import eu.eurlex.celex.dto.ValidationResult;
import eu.eurlex.celex.model.Sector;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.time.Year;

/**
 * REST endpoint for CELEX number validation and parsing.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/validate?celex=32006L0112} – validate and parse a single CELEX number</li>
 *   <li>{@code GET /api/sectors}                   – list all known sectors</li>
 *   <li>{@code GET /api/document-types}            – list all known document types</li>
 * </ul>
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class CelexResource {

    private static final Logger LOG = Logger.getLogger(CelexResource.class);

    @Inject
    CelexParser parser;

    @Inject
    EliUrlBuilder urlBuilder;

    @GET
    @Path("/validate")
    public ValidationResult validate(@QueryParam("celex") String celex) {
        if (celex == null || celex.isBlank()) {
            LOG.debug("validate: empty or missing celex parameter");
            return ValidationResult.invalid(celex, "No CELEX number provided");
        }
        if (celex.length() > 64) {
            LOG.debugf("validate: input too long (%d chars)", celex.length());
            return ValidationResult.invalid(celex, "Input too long");
        }
        return parser.parse(celex)
                .map(c -> {
                    String warning = publishingWarning(c);
                    if (warning != null) {
                        LOG.infof("validate: valid with warning celex=%s warning=%s", safe(c.raw()), warning);
                    } else {
                        LOG.debugf("validate: valid celex=%s sector=%s year=%s type=%s number=%s",
                                safe(c.raw()), c.sector().code, c.year(),
                                c.documentType() != null ? c.documentType().code : "-", c.number());
                    }
                    return ValidationResult.of(c, urlBuilder.build(c), warning);
                })
                .orElseGet(() -> {
                    LOG.debugf("validate: invalid celex=%s", safe(celex));
                    return ValidationResult.invalid(celex,
                            "'" + celex + "' is not a recognised CELEX number");
                });
    }

    /**
     * Returns a warning message when the CELEX year is in the current year or the future
     * for legislation sectors, since those documents may not yet be published in EUR-Lex.
     */
    private static String publishingWarning(eu.eurlex.celex.model.CelexComponents c) {
        if (c.year() == null) return null;
        if (c.sector() != Sector.SECONDARY_LEGISLATION
                && c.sector() != Sector.COMPLEMENTARY_LEGISLATION) return null;
        try {
            int docYear = Integer.parseInt(c.year());
            int currentYear = Year.now().getValue();
            if (docYear >= currentYear) {
                return "The CELEX number is syntactically valid, but the document may not yet be "
                        + "published in EUR-Lex — the generated URL should be verified.";
            }
        } catch (NumberFormatException ignored) {
            // year is not a plain integer; no warning
        }
        return null;
    }

    /**
     * Makes user-supplied input safe to log: truncates it and replaces control
     * characters so a client cannot forge log lines with embedded CR/LF.
     */
    private static String safe(String s) {
        if (s == null) return "null";
        String t = s.length() > 64 ? s.substring(0, 64) + "…" : s;
        return t.replaceAll("\\p{Cntrl}", "?");
    }

    @GET
    @Path("/sectors")
    public Object sectors() {
        LOG.debug("sectors: listing all sectors");
        return java.util.Arrays.stream(eu.eurlex.celex.model.Sector.values())
                .map(s -> java.util.Map.of("code", s.code, "description", s.description))
                .toList();
    }

    @GET
    @Path("/document-types")
    public Object documentTypes(@QueryParam("sector") String sector) {
        if (sector != null && !sector.isBlank()) {
            LOG.debugf("document-types: listing for sector=%s", safe(sector));
        } else {
            LOG.debug("document-types: listing all");
        }
        var stream = java.util.Arrays.stream(eu.eurlex.celex.model.DocumentType.values());
        if (sector != null && !sector.isBlank()) {
            stream = stream.filter(dt -> dt.sectors.contains(sector.toUpperCase()));
        }
        return stream
                .map(dt -> java.util.Map.of(
                        "code", dt.code,
                        "description", dt.description,
                        "sectors", dt.sectors))
                .toList();
    }
}
