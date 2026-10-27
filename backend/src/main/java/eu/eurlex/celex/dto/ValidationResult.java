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
        String error
) {
    /** Build a successful result from parsed components. */
    public static ValidationResult of(CelexComponents c) {
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
                null
        );
    }

    /** Build an invalid result with an error message. */
    public static ValidationResult invalid(String input, String error) {
        return new ValidationResult(false, input, null, null, null, null, null, null, null, null, error);
    }
}
