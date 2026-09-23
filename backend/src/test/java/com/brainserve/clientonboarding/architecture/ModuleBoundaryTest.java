package com.brainserve.clientonboarding.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.brainserve.clientonboarding");
    }

    @Test
    void commonDoesNotDependOnBusinessModules() {
        noClasses()
                .that().resideInAPackage("..common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.brainserve.clientonboarding.auth..",
                        "com.brainserve.clientonboarding.identity..",
                        "com.brainserve.clientonboarding.organization..",
                        "com.brainserve.clientonboarding.client..",
                        "com.brainserve.clientonboarding.servicecatalog..",
                        "com.brainserve.clientonboarding.project..",
                        "com.brainserve.clientonboarding.workflow..",
                        "com.brainserve.clientonboarding.onboarding..",
                        "com.brainserve.clientonboarding.portal..",
                        "com.brainserve.clientonboarding.forms..",
                        "com.brainserve.clientonboarding.assets..",
                        "com.brainserve.clientonboarding.access..",
                        "com.brainserve.clientonboarding.billing..",
                        "com.brainserve.clientonboarding.payments..",
                        "com.brainserve.clientonboarding.contracts..",
                        "com.brainserve.clientonboarding.tasks..",
                        "com.brainserve.clientonboarding.notifications..",
                        "com.brainserve.clientonboarding.reminders..",
                        "com.brainserve.clientonboarding.integrations..",
                        "com.brainserve.clientonboarding.reporting..",
                        "com.brainserve.clientonboarding.audit..")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    @Test
    void domainDoesNotDependOnApiOrInfrastructure() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    @Test
    void controllersDoNotAccessPersistenceDirectly() {
        noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..domain.repository..", "org.springframework.jdbc..", "jakarta.persistence..")
                .check(productionClasses);
    }

    @Test
    void topLevelModulesAreFreeOfDependencyCycles() {
        slices()
                .matching("com.brainserve.clientonboarding.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses);
    }
}
