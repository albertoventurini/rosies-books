package com.albertoventurini.rosiesbooks.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArchitectureRuleFixtureTest {

  private static final String FIXTURES = "com.albertoventurini.rosiesbooks.architecture.fixtures";

  @Test
  void detectsAccessToAnotherFeaturesInternalPackage() {
    assertViolation(
        ArchitectureRules.declaredDependenciesAndApiBoundaries(FIXTURES + ".internalaccess"),
        FIXTURES + ".internalaccess",
        "cross-feature target is not in identity.api");
  }

  @Test
  void detectsUndeclaredFeatureDependency() {
    assertViolation(
        ArchitectureRules.declaredDependenciesAndApiBoundaries(FIXTURES + ".undeclared"),
        FIXTURES + ".undeclared",
        "identity does not declare provider.api");
  }

  @Test
  void detectsFeatureCycle() {
    assertViolation(
        ArchitectureRules.noFeatureCycles(FIXTURES + ".cycle"),
        FIXTURES + ".cycle",
        "Cycle detected");
  }

  @Test
  void detectsPersistenceInfrastructureOutsideAdapters() {
    assertViolation(
        ArchitectureRules.persistenceTypesStayInPersistenceAdapters(FIXTURES + ".persistence"),
        FIXTURES + ".persistence",
        "org.jooq.DSLContext");
    assertViolation(
        ArchitectureRules.persistenceTypesStayInPersistenceAdapters(FIXTURES + ".persistence"),
        FIXTURES + ".persistence",
        "java.sql.Connection");
  }

  @Test
  void detectsWebInfrastructureOutsideAdapters() {
    assertViolation(
        ArchitectureRules.webTypesStayInWebAdapters(FIXTURES + ".web"),
        FIXTURES + ".web",
        "jakarta.ws.rs.Path");
    assertViolation(
        ArchitectureRules.webTypesStayInWebAdapters(FIXTURES + ".web"),
        FIXTURES + ".web",
        "io.quarkus.qute.Template");
  }

  @Test
  void detectsAUserEditionIdOnlyOperation() {
    assertViolation(
        ArchitectureRules.userEditionOperationsRequireCurrentUser(FIXTURES + ".ownership"),
        FIXTURES + ".ownership",
        "accepts UserEdition identity without CurrentUser");
  }

  private static void assertViolation(ArchRule rule, String fixturePackage, String expectedText) {
    JavaClasses classes = new ClassFileImporter().importPackages(fixturePackage);
    String report = rule.evaluate(classes).getFailureReport().toString();
    assertTrue(
        report.contains(expectedText),
        () -> "Expected violation containing '" + expectedText + "' but got:\n" + report);
  }
}
