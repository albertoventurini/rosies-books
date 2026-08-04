package com.albertoventurini.rosiesbooks.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.Set;

final class ArchitectureRules {

  private static final Set<String> FEATURES = Set.of("library", "identity", "provider", "platform");

  private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES =
      Map.of(
          "library", Set.of("identity.api", "provider.api", "platform.api"),
          "identity", Set.of("platform.api"),
          "provider", Set.of("platform.api"),
          "platform", Set.of());

  private ArchitectureRules() {}

  static ArchRule declaredDependenciesAndApiBoundaries(String basePackage) {
    return classes()
        .that()
        .resideInAPackage(basePackage + "..")
        .should(respectFeatureBoundaries(basePackage))
        .allowEmptyShould(false)
        .as("features only access declared APIs of other features");
  }

  static ArchRule noFeatureCycles(String basePackage) {
    return slices()
        .matching(basePackage + ".(*)..")
        .should()
        .beFreeOfCycles()
        .allowEmptyShould(false)
        .as("features are free of dependency cycles");
  }

  static ArchRule persistenceTypesStayInPersistenceAdapters(String basePackage) {
    return classes()
        .that()
        .resideInAPackage(basePackage + "..")
        .should(confinePersistenceTypes(basePackage))
        .allowEmptyShould(false)
        .as("jOOQ, JDBC, and PostgreSQL types stay in persistence or database packages");
  }

  static ArchRule webTypesStayInWebAdapters(String basePackage) {
    return classes()
        .that()
        .resideInAPackage(basePackage + "..")
        .should(confineWebTypes(basePackage))
        .allowEmptyShould(false)
        .as("REST and Qute types stay in web adapters or platform web bootstrap");
  }

  static ArchRule oidcTypesStayInIdentityAuthentication(String basePackage) {
    return classes()
        .that()
        .resideInAPackage(basePackage + "..")
        .should(confineOidcTypes(basePackage))
        .allowEmptyShould(false)
        .as("OIDC types stay in identity authentication adapters");
  }

  static ArchRule userEditionOperationsRequireCurrentUser(String basePackage) {
    return methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage(basePackage + ".library..")
        .should(requireCurrentUserForUserEditionOperations())
        .allowEmptyShould(false)
        .as("library repository and service UserEdition operations require CurrentUser");
  }

  private static ArchCondition<JavaClass> respectFeatureBoundaries(String basePackage) {
    return new ArchCondition<>("respect declared feature dependencies and API boundaries") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        String sourceFeature = featureOf(source.getPackageName(), basePackage);
        if (sourceFeature == null) {
          return;
        }
        for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
          String targetPackage = dependency.getTargetClass().getPackageName();
          String targetFeature = featureOf(targetPackage, basePackage);
          if (targetFeature == null || sourceFeature.equals(targetFeature)) {
            continue;
          }

          String requiredDeclaration = targetFeature + ".api";
          if (!ALLOWED_DEPENDENCIES.get(sourceFeature).contains(requiredDeclaration)) {
            events.add(
                SimpleConditionEvent.violated(
                    dependency,
                    dependency.getDescription()
                        + " ("
                        + sourceFeature
                        + " does not declare "
                        + requiredDeclaration
                        + ")"));
          } else if (!isApiPackage(targetPackage, basePackage, targetFeature)) {
            events.add(
                SimpleConditionEvent.violated(
                    dependency,
                    dependency.getDescription()
                        + " (cross-feature target is not in "
                        + requiredDeclaration
                        + ")"));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> confinePersistenceTypes(String basePackage) {
    return confineDependencies(
        "depend on persistence infrastructure only from a persistence adapter",
        target ->
            belongsToPackage(target, "org.jooq")
                || belongsToPackage(target, "java.sql")
                || belongsToPackage(target, "javax.sql")
                || belongsToPackage(target, "org.postgresql"),
        source ->
            isFeatureAdapter(source, basePackage, "persistence")
                || belongsToPackage(source, basePackage + ".platform.database"));
  }

  private static ArchCondition<JavaClass> confineWebTypes(String basePackage) {
    return confineDependencies(
        "depend on REST or Qute only from a web adapter",
        target ->
            belongsToPackage(target, "jakarta.ws.rs")
                || belongsToPackage(target, "io.quarkus.rest")
                || belongsToPackage(target, "org.jboss.resteasy.reactive")
                || belongsToPackage(target, "io.quarkus.qute"),
        source ->
            isFeatureAdapter(source, basePackage, "web")
                || belongsToPackage(source, basePackage + ".platform.web"));
  }

  private static ArchCondition<JavaClass> confineOidcTypes(String basePackage) {
    return confineDependencies(
        "depend on OIDC only from an identity authentication adapter",
        target -> belongsToPackage(target, "io.quarkus.oidc"),
        source -> belongsToPackage(source, basePackage + ".identity.authentication"));
  }

  private static ArchCondition<JavaMethod> requireCurrentUserForUserEditionOperations() {
    return new ArchCondition<>(
        "accept CurrentUser whenever they accept UserEdition or UserEditionId") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (!method.getOwner().getSimpleName().endsWith("Repository")
            && !method.getOwner().getSimpleName().endsWith("Service")) {
          return;
        }
        Set<String> parameterTypes =
            method.getRawParameterTypes().stream()
                .map(JavaClass::getName)
                .collect(java.util.stream.Collectors.toSet());
        boolean acceptsUserEdition =
            parameterTypes.contains(
                    "com.albertoventurini.rosiesbooks.library.persistence.UserEdition")
                || parameterTypes.contains(
                    "com.albertoventurini.rosiesbooks.library.internal.UserEditionId");
        boolean acceptsCurrentUser =
            parameterTypes.contains("com.albertoventurini.rosiesbooks.identity.api.CurrentUser");
        if (acceptsUserEdition && !acceptsCurrentUser) {
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName() + " accepts UserEdition identity without CurrentUser"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> confineDependencies(
      String description,
      java.util.function.Predicate<String> isInfrastructurePackage,
      java.util.function.Predicate<String> isAllowedSourcePackage) {
    return new ArchCondition<>(description) {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        if (isAllowedSourcePackage.test(source.getPackageName())) {
          return;
        }
        for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
          if (isInfrastructurePackage.test(dependency.getTargetClass().getPackageName())) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
      }
    };
  }

  private static String featureOf(String packageName, String basePackage) {
    String prefix = basePackage + ".";
    if (!packageName.startsWith(prefix)) {
      return null;
    }
    String remainder = packageName.substring(prefix.length());
    String feature =
        remainder.contains(".") ? remainder.substring(0, remainder.indexOf('.')) : remainder;
    return FEATURES.contains(feature) ? feature : null;
  }

  private static boolean isApiPackage(
      String packageName, String basePackage, String targetFeature) {
    String apiPackage = basePackage + "." + targetFeature + ".api";
    return packageName.equals(apiPackage) || packageName.startsWith(apiPackage + ".");
  }

  private static boolean isFeatureAdapter(
      String packageName, String basePackage, String adapterPackage) {
    for (String feature : FEATURES) {
      String adapterRoot = basePackage + "." + feature + "." + adapterPackage;
      if (packageName.equals(adapterRoot) || packageName.startsWith(adapterRoot + ".")) {
        return true;
      }
    }
    return false;
  }

  private static boolean belongsToPackage(String packageName, String packageRoot) {
    return packageName.equals(packageRoot) || packageName.startsWith(packageRoot + ".");
  }
}
