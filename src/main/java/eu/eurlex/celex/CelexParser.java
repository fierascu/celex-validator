package eu.eurlex.celex;

import eu.eurlex.celex.model.CelexComponents;
import eu.eurlex.celex.model.DocumentType;
import eu.eurlex.celex.model.Sector;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and validates CELEX numbers according to the EUR-Lex specification.
 *
 * <p>CELEX number structure:
 * <pre>
 *   [Sector][Year][DocumentType][Number][Suffix?]
 * </pre>
 *
 * <p>Rules per sector:
 * <ul>
 *   <li><b>1</b> – Treaties: 1YYYYTT[number] or 1[number]  (year optional for old treaties)</li>
 *   <li><b>2</b> – External agreements: 2YYYYA[number] or 2YYYYD[number]</li>
 *   <li><b>3,4</b> – Legislation: 3YYYYxx[number] where xx is 1–2 char type code</li>
 *   <li><b>5</b> – Preparatory acts: 5YYYYXX[number]  (2-char type code)</li>
 *   <li><b>6</b> – Case law: 6YYYYxx[number]  (2-char type code)</li>
 *   <li><b>7</b> – National implementation: 7YYYY[Member State code][number]</li>
 *   <li><b>9</b> – Parliamentary questions: 9YYYYWQ[number] or 9YYYYOQ[number]</li>
 *   <li><b>C,E</b> – OJ C / EFTA: CYYYYXX[number] or EYYYYXX[number]</li>
 * </ul>
 *
 * <p>Suffixes (optional, after the number):
 * <ul>
 *   <li>{@code /TXT} – full text</li>
 *   <li>{@code (01)} – corrigendum or part number</li>
 *   <li>{@code R(01)} – rectification</li>
 *   <li>{@code /ANN} – annex</li>
 * </ul>
 */
@ApplicationScoped
public class CelexParser {

    // Suffix pattern: /TXT, /ANN, (01), R(01), etc.
    private static final Pattern SUFFIX_PATTERN =
            Pattern.compile("(/[A-Z]{2,4}|R?\\(\\d{2}\\))$", Pattern.CASE_INSENSITIVE);

    // Body (everything before the suffix): sector + alphanumerics, plus '_' used by
    // national implementation measures (e.g. 72014L0065DEU_202001398). Rejecting every
    // other character here keeps HTML, IRI and URL metacharacters out of all outputs.
    private static final Pattern BODY_PATTERN = Pattern.compile("^[0-9CE][0-9A-Z_]{1,40}$", Pattern.CASE_INSENSITIVE);

    // Year: 4 digits, reasonable range
    private static final Pattern YEAR_PATTERN = Pattern.compile("^(1[0-9]{3}|2[0-9]{3})$");

    // Sector 3/4 single-char document type codes
    private static final String LEG_SINGLE_TYPES = "RLDFJEHOQGKBIMXY";

    // Sector 5 two-char document type codes
    private static final java.util.Set<String> PREP_TYPES =
            java.util.Set.of("PC", "DC", "AC", "AP", "IC", "GC", "WC", "SC", "EC", "CC", "AG", "SG");

    // Sector 6 case-law two-char type codes
    private static final java.util.Set<String> CASELAW_TYPES =
            java.util.Set.of("CJ", "CO", "CC", "TJ", "TC", "TO", "FJ", "FC", "FO", "TA");

    /**
     * Attempt to parse {@code input} as a CELEX number.
     *
     * @param input the raw CELEX string (whitespace is trimmed)
     * @return an {@link Optional} containing parsed components, or empty if invalid
     */
    public Optional<CelexComponents> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String raw = input.strip();

        // Strip known suffix first
        String suffix = null;
        String body = raw;
        Matcher sm = SUFFIX_PATTERN.matcher(raw);
        if (sm.find()) {
            suffix = sm.group();
            body = raw.substring(0, sm.start());
        }

        if (!BODY_PATTERN.matcher(body).matches()) return Optional.empty();

        String sectorCode = String.valueOf(body.charAt(0)).toUpperCase();
        Sector sector = Sector.fromCode(sectorCode);
        if (sector == null) return Optional.empty();

        String rest = body.substring(1); // everything after the sector digit

        return switch (sectorCode) {
            case "1" -> parseTreaties(raw, rest, sector, suffix);
            case "2" -> parseExternalAgreements(raw, rest, sector, suffix);
            case "3", "4" -> parseLegislation(raw, sectorCode, rest, sector, suffix);
            case "5" -> parsePreparatoryActs(raw, rest, sector, suffix);
            case "6" -> parseCaseLaw(raw, rest, sector, suffix);
            case "7" -> parseNationalImplementation(raw, rest, sector, suffix);
            case "9" -> parseParliamentaryQuestions(raw, rest, sector, suffix);
            case "C", "E" -> parseOtherSectors(raw, sectorCode, rest, sector, suffix);
            default -> Optional.empty();
        };
    }

    // -------------------------------------------------------------------------
    // Sector 1 – Treaties
    // Format: 1[YYYY][TypeCode][Number]  e.g. 12002E/TXT, 11957E012
    // -------------------------------------------------------------------------
    private Optional<CelexComponents> parseTreaties(String raw, String rest, Sector sector, String suffix) {
        if (rest.length() < 2) return Optional.empty();

        // Try to extract 4-digit year
        if (rest.length() >= 4 && isYear(rest.substring(0, 4))) {
            String year = rest.substring(0, 4);
            String typeAndNum = rest.substring(4);
            if (typeAndNum.isEmpty()) return Optional.empty();

            // Single char type code
            String typeCode = String.valueOf(typeAndNum.charAt(0)).toUpperCase();
            String number = typeAndNum.length() > 1 ? typeAndNum.substring(1) : "";
            DocumentType dt = DocumentType.fromCodeAndSector(typeCode, "1");

            return buildResult(raw, sector, year, dt, typeCode, number, suffix,
                    "Treaty document (" + (dt != null ? dt.description : "type " + typeCode) + ")"
                            + (number.isEmpty() ? "" : " no. " + number)
                            + ", year " + year);
        }

        // No year prefix (very old treaties) — just type + number
        String typeCode = String.valueOf(rest.charAt(0)).toUpperCase();
        String number = rest.length() > 1 ? rest.substring(1) : "";
        DocumentType dt = DocumentType.fromCodeAndSector(typeCode, "1");

        return buildResult(raw, sector, null, dt, typeCode, number, suffix,
                "Treaty document (" + (dt != null ? dt.description : "type " + typeCode) + ")"
                        + (number.isEmpty() ? "" : " no. " + number));
    }

    // -------------------------------------------------------------------------
    // Sector 2 – External agreements
    // Format: 2[YYYY][A|D][Number]  e.g. 22013A0907(01)
    // -------------------------------------------------------------------------
    private Optional<CelexComponents> parseExternalAgreements(String raw, String rest, Sector sector, String suffix) {
        if (rest.length() < 5) return Optional.empty();
        if (!isYear(rest.substring(0, 4))) return Optional.empty();

        String year = rest.substring(0, 4);
        String typeCode = String.valueOf(rest.charAt(4)).toUpperCase();
        String number = rest.length() > 5 ? rest.substring(5) : "";
        DocumentType dt = DocumentType.fromCodeAndSector(typeCode, "2");

        return buildResult(raw, sector, year, dt, typeCode, number, suffix,
                "External agreement"
                        + (dt != null ? " – " + dt.description : "")
                        + ", year " + year
                        + (number.isEmpty() ? "" : ", ref. " + number));
    }

    // -------------------------------------------------------------------------
    // Sectors 3 & 4 – Secondary / Complementary legislation
    // Format: [3|4][YYYY][TypeCode(1-2 chars)][Number]  e.g. 32006L0112, 32023R2854
    // -------------------------------------------------------------------------
    private Optional<CelexComponents> parseLegislation(String raw, String sectorCode, String rest,
                                                        Sector sector, String suffix) {
        if (rest.length() < 5) return Optional.empty();
        if (!isYear(rest.substring(0, 4))) return Optional.empty();

        String year = rest.substring(0, 4);
        String afterYear = rest.substring(4);
        if (afterYear.isEmpty()) return Optional.empty();

        // Try 2-char type code first (XR, XL, etc.), then 1-char
        String typeCode;
        String number;
        if (afterYear.length() >= 2
                && Character.isLetter(afterYear.charAt(0))
                && Character.isLetter(afterYear.charAt(1))
                && afterYear.length() > 2
                && !Character.isLetter(afterYear.charAt(2))) {
            typeCode = afterYear.substring(0, 2).toUpperCase();
            number = afterYear.substring(2);
        } else {
            typeCode = String.valueOf(afterYear.charAt(0)).toUpperCase();
            number = afterYear.length() > 1 ? afterYear.substring(1) : "";
        }

        if (!isValidLegDocType(typeCode)) return Optional.empty();

        DocumentType dt = DocumentType.fromCodeAndSector(typeCode, sectorCode);

        String desc = (dt != null ? dt.description : "Document type " + typeCode)
                + ", year " + year
                + (number.isEmpty() ? "" : ", no. " + number)
                + (suffix != null ? " [" + suffix + "]" : "");

        return buildResult(raw, sector, year, dt, typeCode, number, suffix, desc);
    }

    // -------------------------------------------------------------------------
    // Sector 5 – Preparatory acts
    // Format: 5[YYYY][XX][Number]  e.g. 52023PC0206
    // -------------------------------------------------------------------------
    private Optional<CelexComponents> parsePreparatoryActs(String raw, String rest, Sector sector, String suffix) {
        if (rest.length() < 6) return Optional.empty();
        if (!isYear(rest.substring(0, 4))) return Optional.empty();

        String year = rest.substring(0, 4);
        String typeCode = rest.substring(4, 6).toUpperCase();
        String number = rest.length() > 6 ? rest.substring(6) : "";

        if (!PREP_TYPES.contains(typeCode)) return Optional.empty();

        DocumentType dt = DocumentType.fromCodeAndSector(typeCode, "5");

        return buildResult(raw, sector, year, dt, typeCode, number, suffix,
                (dt != null ? dt.description : "Preparatory act (" + typeCode + ")")
                        + ", year " + year
                        + (number.isEmpty() ? "" : ", ref. " + number));
    }

    // -------------------------------------------------------------------------
    // Sector 6 – Case law
    // Format: 6[YYYY][XX][Number]  e.g. 62019CJ0001, 61990CJ0213
    // -------------------------------------------------------------------------
    private Optional<CelexComponents> parseCaseLaw(String raw, String rest, Sector sector, String suffix) {
        if (rest.length() < 6) return Optional.empty();
        if (!isYear(rest.substring(0, 4))) return Optional.empty();

        String year = rest.substring(0, 4);
        String typeCode = rest.substring(4, 6).toUpperCase();
        String number = rest.length() > 6 ? rest.substring(6) : "";

        if (!CASELAW_TYPES.contains(typeCode)) return Optional.empty();

        DocumentType dt = DocumentType.fromCodeAndSector(typeCode, "6");

        return buildResult(raw, sector, year, dt, typeCode, number, suffix,
                (dt != null ? dt.description : "Case law (" + typeCode + ")")
                        + ", year " + year
                        + (number.isEmpty() ? "" : ", case no. " + number));
    }

    // -------------------------------------------------------------------------
    // Sector 7 – National implementation measures
    // Format: 7[YYYY][MS2][Number]  e.g. 72006L0112DEU
    // -------------------------------------------------------------------------
    private Optional<CelexComponents> parseNationalImplementation(String raw, String rest,
                                                                    Sector sector, String suffix) {
        if (rest.length() < 4) return Optional.empty();
        if (!isYear(rest.substring(0, 4))) return Optional.empty();

        String year = rest.substring(0, 4);
        String number = rest.length() > 4 ? rest.substring(4) : "";

        return buildResult(raw, sector, year, null, "NI", number, suffix,
                "National implementation measure, year " + year
                        + (number.isEmpty() ? "" : ", ref. " + number));
    }

    // -------------------------------------------------------------------------
    // Sector 9 – Parliamentary questions
    // Format: 9[YYYY][WQ|OQ][Number]  e.g. 92020WQ000123
    // -------------------------------------------------------------------------
    private Optional<CelexComponents> parseParliamentaryQuestions(String raw, String rest,
                                                                    Sector sector, String suffix) {
        if (rest.length() < 6) return Optional.empty();
        if (!isYear(rest.substring(0, 4))) return Optional.empty();

        String year = rest.substring(0, 4);
        String typeCode = rest.substring(4, 6).toUpperCase();
        String number = rest.length() > 6 ? rest.substring(6) : "";

        if (!typeCode.equals("WQ") && !typeCode.equals("OQ")) return Optional.empty();

        DocumentType dt = DocumentType.fromCodeAndSector(typeCode, "9");

        return buildResult(raw, sector, year, dt, typeCode, number, suffix,
                (dt != null ? dt.description : "Parliamentary question (" + typeCode + ")")
                        + ", year " + year
                        + (number.isEmpty() ? "" : ", no. " + number));
    }

    // -------------------------------------------------------------------------
    // Sectors C, E – OJ C-series / EFTA
    // Format: C[YYYY][XX][Number]
    // -------------------------------------------------------------------------
    private Optional<CelexComponents> parseOtherSectors(String raw, String sectorCode, String rest,
                                                          Sector sector, String suffix) {
        if (rest.length() < 4) return Optional.empty();
        if (!isYear(rest.substring(0, 4))) return Optional.empty();

        String year = rest.substring(0, 4);
        String afterYear = rest.substring(4);
        String typeCode = afterYear.length() >= 2 ? afterYear.substring(0, 2).toUpperCase()
                : afterYear.length() == 1 ? afterYear.substring(0, 1).toUpperCase() : "";
        String number = afterYear.length() > typeCode.length() ? afterYear.substring(typeCode.length()) : "";

        return buildResult(raw, sector, year, null, typeCode, number, suffix,
                (sectorCode.equals("C") ? "OJ C-series document" : "EFTA document")
                        + ", year " + year
                        + (typeCode.isEmpty() ? "" : ", type " + typeCode)
                        + (number.isEmpty() ? "" : ", no. " + number));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isYear(String s) {
        return s != null && s.length() == 4 && YEAR_PATTERN.matcher(s).matches();
    }

    private boolean isValidLegDocType(String code) {
        if (code == null || code.isEmpty()) return false;
        if (code.length() == 1) return LEG_SINGLE_TYPES.indexOf(code.charAt(0)) >= 0;
        // Accept any 2-char alpha combination for delegated/implementing variants
        return code.length() == 2
                && Character.isLetter(code.charAt(0))
                && Character.isLetter(code.charAt(1));
    }

    private Optional<CelexComponents> buildResult(String raw, Sector sector, String year,
                                                    DocumentType dt, String typeCode,
                                                    String number, String suffix, String description) {
        // A number must be non-empty for most sectors, but suffixes can substitute
        if (number.isEmpty() && suffix == null && sector != Sector.TREATIES) {
            return Optional.empty();
        }
        return Optional.of(new CelexComponents(raw, sector, year, dt, number, suffix, description));
    }
}
