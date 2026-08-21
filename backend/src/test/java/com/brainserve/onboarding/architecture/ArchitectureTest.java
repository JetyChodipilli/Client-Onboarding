package com.brainserve.onboarding.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.brainserve.onboarding", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule api_must_not_depend_on_persistence_implementations = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule identity_persistence_is_private_to_identity_module = noClasses()
            .that().resideOutsideOfPackage("..identity..")
            .should().dependOnClassesThat().resideInAPackage("..identity.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule organization_persistence_is_private_to_organization_module = noClasses()
            .that().resideOutsideOfPackage("..organization..")
            .should().dependOnClassesThat().resideInAPackage("..organization.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule client_persistence_is_private_to_client_module = noClasses()
            .that().resideOutsideOfPackage("..client..")
            .should().dependOnClassesThat().resideInAPackage("..client.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule service_catalog_persistence_is_private_to_service_catalog_module = noClasses()
            .that().resideOutsideOfPackage("..servicecatalog..")
            .should().dependOnClassesThat().resideInAPackage("..servicecatalog.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule project_persistence_is_private_to_project_module = noClasses()
            .that().resideOutsideOfPackage("..project..")
            .should().dependOnClassesThat().resideInAPackage("..project.infrastructure.persistence..")
            .allowEmptyShould(true);


    @ArchTest
    static final ArchRule workflow_persistence_is_private_to_workflow_module = noClasses()
            .that().resideOutsideOfPackage("..workflow..")
            .should().dependOnClassesThat().resideInAPackage("..workflow.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule onboarding_persistence_is_private_to_onboarding_module = noClasses()
            .that().resideOutsideOfPackage("..onboarding..")
            .should().dependOnClassesThat().resideInAPackage("..onboarding.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule forms_persistence_is_private_to_forms_module = noClasses()
            .that().resideOutsideOfPackage("..forms..")
            .should().dependOnClassesThat().resideInAPackage("..forms.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule assets_persistence_is_private_to_assets_module = noClasses()
            .that().resideOutsideOfPackage("..assets..")
            .should().dependOnClassesThat().resideInAPackage("..assets.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule contracts_persistence_is_private_to_contracts_module = noClasses()
            .that().resideOutsideOfPackage("..contracts..")
            .should().dependOnClassesThat().resideInAPackage("..contracts.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule access_persistence_is_private_to_access_module = noClasses()
            .that().resideOutsideOfPackage("..access..")
            .should().dependOnClassesThat().resideInAPackage("..access.infrastructure.persistence..")
            .allowEmptyShould(true);

    // Billing and payments deliberately form one financial consistency boundary: verified provider events must
    // atomically append the payment ledger and reconcile the invoice. No non-financial module may reach either
    // persistence package directly; see ADR 0007.
    @ArchTest
    static final ArchRule billing_persistence_is_private_to_financial_boundary = noClasses()
            .that().resideOutsideOfPackages("..billing..", "..payments..")
            .should().dependOnClassesThat().resideInAPackage("..billing.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule payments_persistence_is_private_to_financial_boundary = noClasses()
            .that().resideOutsideOfPackages("..billing..", "..payments..")
            .should().dependOnClassesThat().resideInAPackage("..payments.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule tasks_persistence_is_private_to_tasks_module = noClasses()
            .that().resideOutsideOfPackage("..tasks..")
            .should().dependOnClassesThat().resideInAPackage("..tasks.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule notifications_persistence_is_private_to_notifications_module = noClasses()
            .that().resideOutsideOfPackage("..notifications..")
            .should().dependOnClassesThat().resideInAPackage("..notifications.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule reminders_persistence_is_private_to_reminders_module = noClasses()
            .that().resideOutsideOfPackage("..reminders..")
            .should().dependOnClassesThat().resideInAPackage("..reminders.infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_must_not_depend_on_api = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..api..")
            .allowEmptyShould(true);
}
