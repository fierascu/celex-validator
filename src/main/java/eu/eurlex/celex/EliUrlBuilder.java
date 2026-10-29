package eu.eurlex.celex;

import eu.eurlex.celex.model.CelexComponents;
import eu.eurlex.celex.model.Sector;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds the EUR-Lex URL for a parsed CELEX number.
 *
 * <p>For secondary legislation (sectors 3 and 4) the <em>ELI</em>
 * (European Legislation Identifier) canonical URL is used:
 * <pre>https://eur-lex.europa.eu/eli/{type-slug}/{year}/{number}/oj/eng</pre>
 *
 * <p>For everything else the generic CELEX deep-link is used:
 * <pre>https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:{celex}</pre>
 */
@ApplicationScoped
public class EliUrlBuilder {

    private static final String ELI_BASE  = "https://eur-lex.europa.eu/eli";
    private static final String CELEX_URL = "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:";

    private static final Pattern SAFE_CELEX = Pattern.compile("[A-Za-z0-9_/()]+");

    /**
     * ELI type slugs for secondary/complementary legislation document type codes.
     * Only types that have a well-defined ELI path are listed here.
     */
    private static final Map<String, String> ELI_SLUGS = Map.ofEntries(
            Map.entry("R", "reg"),
            Map.entry("L", "dir"),
            Map.entry("D", "dec"),
            Map.entry("F", "dec_framw"),
            Map.entry("H", "reco"),
            Map.entry("O", "opinion"),
            Map.entry("X", "reg_impl"),
            Map.entry("Y", "reg_del")
    );

    /**
     * Returns the best EUR-Lex URL for the given parsed CELEX components.
     *
     * @param c parsed components — must not be null
     * @return absolute EUR-Lex URL string
     */
    public String build(CelexComponents c) {
        // Sectors 3 and 4 (secondary / complementary legislation) → ELI URL
        if (c.sector() == Sector.SECONDARY_LEGISLATION
                || c.sector() == Sector.COMPLEMENTARY_LEGISLATION) {

            String typeCode = c.documentType() != null
                    ? c.documentType().code
                    : (c.number() != null && !c.number().isBlank() ? null : null);

            if (typeCode != null && ELI_SLUGS.containsKey(typeCode)
                    && c.year() != null && c.number() != null && !c.number().isBlank()) {

                String slug   = ELI_SLUGS.get(typeCode);
                String year   = c.year();
                // Strip leading zeros from the number for ELI (0112 → 112, 0679 → 679)
                String number = stripLeadingZeros(c.number());
                String suffix = c.suffix() != null ? c.suffix() : "oj";
                // /TXT suffix means we want the text view — use oj for the ELI base
                if (suffix.startsWith("/")) suffix = "oj";

                return ELI_BASE + "/" + slug + "/" + year + "/" + number + "/" + suffix + "/eng";
            }
        }

        // Fallback: generic CELEX deep-link (works for case law, preparatory acts,
        // treaties, parliamentary questions, etc.)
        return CELEX_URL + encode(c.raw());
    }

    private static String stripLeadingZeros(String s) {
        if (s == null || s.isBlank()) return s;
        // Keep at least one digit; strip leading zeros only from purely numeric parts
        String stripped = s.replaceFirst("^0+(?!$)", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private static String encode(String raw) {
        // Valid CELEX numbers only contain these characters, which are safe in a query
        // string; anything else is percent-encoded as defence in depth.
        if (SAFE_CELEX.matcher(raw).matches()) return raw;
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}
