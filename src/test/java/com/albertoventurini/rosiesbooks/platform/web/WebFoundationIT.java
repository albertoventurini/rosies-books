package com.albertoventurini.rosiesbooks.platform.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
class WebFoundationIT {

  @Test
  void servesTheFoundationPageFromThePackagedApplication() {
    given()
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .contentType("text/html; charset=UTF-8")
        .body(containsString("<h1>Rosie&#39;s books</h1>"));
  }

  @Test
  void servesPackagedStaticAssets() {
    given()
        .when()
        .get("/assets/app.css")
        .then()
        .statusCode(200)
        .contentType("text/css")
        .body(containsString("--color-surface: #f3ede2"));
    given()
        .when()
        .get("/assets/fonts/newsreader-latin-v1.woff2")
        .then()
        .statusCode(200)
        .contentType("font/woff2");
  }

  @Test
  void servesPackagedHealthProbes() {
    given().when().get("/q/health/started").then().statusCode(200).body("status", is("UP"));
    given().when().get("/q/health/live").then().statusCode(200).body("status", is("UP"));
    given().when().get("/q/health/ready").then().statusCode(200).body("status", is("UP"));
  }

  @Test
  void packagedProductionDoesNotExposeTheDevelopmentUserSelector() {
    given().when().get("/dev/users").then().statusCode(404);
    given()
        .formParam("alias", "reader-one")
        .when()
        .post("/dev/users")
        .then()
        .statusCode(404)
        .header("Set-Cookie", nullValue());
  }
}
