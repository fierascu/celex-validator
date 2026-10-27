package eu.eurlex.celex.model;

/**
 * Immutable value object holding the parsed components of a valid CELEX number.
 *
 * @param raw          the original input string
 * @param sector       the sector enum
 * @param year         the year part (4 digits), null for treaty articles that use article numbers
 * @param documentType the document type enum
 * @param number       the sequential number or identifier part
 * @param suffix       optional suffix (e.g. /TXT, (01), R(01))
 * @param description  human-readable description of what this CELEX identifies
 */
public record CelexComponents(
        String raw,
        Sector sector,
        String year,
        DocumentType documentType,
        String number,
        String suffix,
        String description
) {}
