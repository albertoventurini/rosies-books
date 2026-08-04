package com.albertoventurini.rosiesbooks.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.albertoventurini.rosiesbooks.AppModule;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  private static final String BASE_PACKAGE = "com.albertoventurini.rosiesbooks";
  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages(BASE_PACKAGE);

  @Test
  void featureRootsDeclareTheirModules() {
    assertAll(
        () -> assertModule("library", "identity.api", "provider.api", "platform.api"),
        () -> assertModule("identity", "platform.api"),
        () -> assertModule("provider", "platform.api"),
        () -> assertModule("platform"));
  }

  @Test
  void crossFeatureDependenciesUseOnlyDeclaredApis() {
    ArchitectureRules.declaredDependenciesAndApiBoundaries(BASE_PACKAGE).check(PRODUCTION_CLASSES);
  }

  @Test
  void featuresHaveNoCycles() {
    ArchitectureRules.noFeatureCycles(BASE_PACKAGE).check(PRODUCTION_CLASSES);
  }

  @Test
  void persistenceInfrastructureIsConfinedToAdapters() {
    ArchitectureRules.persistenceTypesStayInPersistenceAdapters(BASE_PACKAGE)
        .check(PRODUCTION_CLASSES);
  }

  @Test
  void webInfrastructureIsConfinedToAdapters() {
    ArchitectureRules.webTypesStayInWebAdapters(BASE_PACKAGE).check(PRODUCTION_CLASSES);
  }

  @Test
  void oidcInfrastructureIsConfinedToIdentityAdapters() {
    ArchitectureRules.oidcTypesStayInIdentityAuthentication(BASE_PACKAGE).check(PRODUCTION_CLASSES);
  }

  @Test
  void userEditionEntryPointsRequireCurrentUser() {
    ArchitectureRules.userEditionOperationsRequireCurrentUser(BASE_PACKAGE)
        .check(PRODUCTION_CLASSES);
  }

  private static void assertModule(String feature, String... allowedDependencies) {
    Package featurePackage;
    try {
      featurePackage = Class.forName(BASE_PACKAGE + "." + feature + ".package-info").getPackage();
    } catch (ClassNotFoundException exception) {
      throw new AssertionError("Missing feature package " + feature, exception);
    }
    AppModule module = featurePackage.getAnnotation(AppModule.class);
    assertNotNull(module, "Missing @AppModule on " + feature);
    assertEquals(feature, module.name());
    assertEquals(
        java.util.List.of(allowedDependencies), java.util.List.of(module.allowedDependencies()));
  }
}
