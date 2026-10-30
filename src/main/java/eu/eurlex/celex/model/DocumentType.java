package eu.eurlex.celex.model;

import java.util.Set;

/**
 * Document type codes used in CELEX numbers.
 * Each code belongs to one or more sectors.
 */
public enum DocumentType {

    // --- Sector 1: Treaties ---
    TREATY_ARTICLE("A", "Treaty article", Set.of("1")),
    TREATY_ANNEX("N", "Treaty annex", Set.of("1")),
    TREATY_PROTOCOL("P", "Treaty protocol", Set.of("1")),
    TREATY_TEXT("E", "Treaty text (full)", Set.of("1")),
    TREATY_FINAL_ACT("F", "Final act", Set.of("1")),
    TREATY_DECLARATION("X", "Declaration", Set.of("1")),

    // --- Sector 2: External agreements ---
    AGREEMENT("A", "Agreement", Set.of("2")),
    AGREEMENT_DECISION("D", "Decision of agreement body", Set.of("2")),
    AGREEMENT_RECOMMENDATION("R", "Recommendation of agreement body", Set.of("2")),

    // --- Sector 3 & 4: Secondary / Complementary legislation ---
    REGULATION("R", "Regulation", Set.of("3", "4")),
    DIRECTIVE("L", "Directive", Set.of("3", "4")),
    DECISION("D", "Decision", Set.of("3", "4")),
    FRAMEWORK_DECISION("F", "Framework decision", Set.of("3", "4")),
    JOINT_ACTION("J", "Joint action", Set.of("3", "4")),
    COMMON_POSITION("E", "Common position", Set.of("3", "4")),
    RECOMMENDATION("H", "Recommendation", Set.of("3", "4")),
    OPINION("O", "Opinion", Set.of("3", "4")),
    RESOLUTION("G", "Resolution", Set.of("3", "4")),
    DECLARATION_LEG("K", "Declaration (legislative)", Set.of("3", "4")),
    INTERINSTITUTIONAL_AGREEMENT("Q", "Interinstitutional agreement", Set.of("3", "4")),
    BUDGET("B", "Budget", Set.of("3", "4")),
    INTERNAL_AGREEMENT("I", "Internal agreement", Set.of("3", "4")),
    MACRO_CONVERGENCE("M", "Macroeconomic convergence decision", Set.of("3")),
    IMPLEMENTING_REGULATION("X", "Implementing regulation", Set.of("3", "4")),
    DELEGATED_REGULATION("Y", "Delegated regulation", Set.of("3", "4")),

    // --- Sector 5: Preparatory acts ---
    COMMISSION_PROPOSAL("PC", "Commission proposal", Set.of("5")),
    COMMISSION_COMMUNICATION("DC", "Commission communication / COM document", Set.of("5")),
    COUNCIL_POSITION("AC", "Council position (common position)", Set.of("5")),
    EP_POSITION("AP", "European Parliament position", Set.of("5")),
    INITIATIVE("IC", "Initiative (Member State)", Set.of("5")),
    GREEN_PAPER("GC", "Green paper", Set.of("5")),
    WHITE_PAPER("WC", "White paper", Set.of("5")),

    // --- Sector 6: Case law ---
    JUDGMENT_CJ("CJ", "Judgment of the Court of Justice", Set.of("6")),
    OPINION_CJ("CO", "Opinion of the Court of Justice", Set.of("6")),
    ORDER_CJ("CC", "Order of the Court of Justice", Set.of("6")),
    JUDGMENT_GC("TJ", "Judgment of the General Court", Set.of("6")),
    ORDER_GC("TC", "Order of the General Court", Set.of("6")),
    JUDGMENT_CST("FJ", "Judgment of the Civil Service Tribunal", Set.of("6")),
    ORDER_CST("FC", "Order of the Civil Service Tribunal", Set.of("6")),

    // --- Sector 7: National implementation ---
    NATIONAL_MEASURE("NI", "National implementation measure", Set.of("7")),

    // --- Sector 9: Parliamentary questions ---
    WRITTEN_QUESTION("WQ", "Written question", Set.of("9")),
    ORAL_QUESTION("OQ", "Oral question", Set.of("9"));

    public final String code;
    public final String description;
    public final Set<String> sectors;

    DocumentType(String code, String description, Set<String> sectors) {
        this.code = code;
        this.description = description;
        this.sectors = sectors;
    }

    /**
     * Find all document types matching a given code within a sector.
     * Multiple entries can share the same code across different sectors.
     */
    public static DocumentType fromCodeAndSector(String code, String sector) {
        if (code == null || sector == null) return null;
        for (DocumentType dt : values()) {
            if (dt.code.equalsIgnoreCase(code) && dt.sectors.contains(sector)) {
                return dt;
            }
        }
        return null;
    }
}
