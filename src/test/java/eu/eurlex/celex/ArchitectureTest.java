package eu.eurlex.celex;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "eu.eurlex.celex")
class ArchitectureTest {

    /**
     * Model classes (records/enums in the model package) must not depend on
     * REST or CDI infrastructure — they are plain data objects.
     */
    @ArchTest
    static final ArchRule modelClassesMustNotDependOnFrameworks =
            noClasses()
                    .that().resideInAPackage("eu.eurlex.celex.model..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.ws.rs..", "jakarta.inject..", "io.quarkus..");

    /**
     * DTO classes must only be accessed from the celex package itself (i.e. by
     * resource / service classes), not from within the model package.
     */
    @ArchTest
    static final ArchRule modelMustNotDependOnDto =
            noClasses()
                    .that().resideInAPackage("eu.eurlex.celex.model..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("eu.eurlex.celex.dto..");
}
