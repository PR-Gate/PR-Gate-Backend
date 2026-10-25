package com.prgate.projects.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.prgate.projects", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

    @ArchTest
    static final ArchRule domainDependsOnlyOnJavaAndItself = noClasses()
            .that().resideInAPackage("com.prgate.projects.domain..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages("java..", "com.prgate.projects.domain..")
            .because("the domain must remain independent of frameworks and external layers");
}
