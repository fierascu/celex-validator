package eu.eurlex.celex.dto;

import eu.eurlex.celex.model.CelexComponents;

/**
 * API response for a CELEX validation/parse request.
 */
public record ValidationResult(
        boolean valid,
        String input,
        String sector,
        String sectorDescription,
        String year,
        String documentTypeCode,
        String documentTypeDescription,
        String number,
        String suffix,
        String humanDescription,
        String eurLexUrl,
        String warning,
        String error
) {
    /** Build a successful result from parsed components and its EUR-Lex URL. */
    public static ValidationResult of(CelexComponents c, String eurLexUrl) {
        return of(c, eurLexUrl, null);
    }

    /** Build a successful result from parsed components, its EUR-Lex URL, and an optional warning. */
    public static ValidationResult of(CelexComponents c, String eurLexUrl, String warning) {
        return new ValidationResult(
                true,
                c.raw(),
                c.sector().code,
                c.sector().description,
                c.year(),
                c.documentType() != null ? c.documentType().code : null,
                c.documentType() != null ? c.documentType().description : null,
                c.number(),
                c.suffix(),
                c.description(),
                eurLexUrl,
                warning,
                null
        );
    }

    /** Build an invalid result with an error message. */
    public static ValidationResult invalid(String input, String error) {
        return new ValidationResult(false, input, null, null, null, null, null, null, null, null, null, null, error);
    }
}
