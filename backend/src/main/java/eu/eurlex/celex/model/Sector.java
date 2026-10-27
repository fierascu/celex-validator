package eu.eurlex.celex.model;

/**
 * CELEX sector codes as defined by EUR-Lex.
 * The sector is the first character of a CELEX number.
 */
public enum Sector {

    TREATIES("1", "Treaties"),
    EXTERNAL_AGREEMENTS("2", "External agreements"),
    SECONDARY_LEGISLATION("3", "Secondary legislation"),
    COMPLEMENTARY_LEGISLATION("4", "Complementary legislation"),
    PREPARATORY_ACTS("5", "Preparatory acts"),
    CASE_LAW("6", "Case law"),
    NATIONAL_IMPLEMENTATION("7", "National implementation measures"),
    NATIONAL_CASE_LAW("8", "National case law references"),
    PARLIAMENTARY_QUESTIONS("9", "Parliamentary questions"),
    OJ_C_SERIES("C", "OJ C-series documents"),
    EFTA("E", "EFTA documents");

    public final String code;
    public final String description;

    Sector(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static Sector fromCode(String code) {
        if (code == null) return null;
        for (Sector s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
