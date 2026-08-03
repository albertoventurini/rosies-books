package com.albertoventurini.rosiesbooks.platform.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthTest {

  @Test
  void reportsStartup() {
    assertUp("/q/health/started", "application-startup");
  }

  @Test
  void reportsLiveness() {
    assertUp("/q/health/live", "application-liveness");
  }

  @Test
  void reportsReadiness() {
    assertUp("/q/health/ready", "application-readiness");
  }

  private static void assertUp(String path, String checkName) {
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .contentType("application/json; charset=UTF-8")
        .body("status", is("UP"))
        .body("checks.name", hasItem(checkName));
  }
}
