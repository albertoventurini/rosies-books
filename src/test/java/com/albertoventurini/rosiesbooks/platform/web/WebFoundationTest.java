package com.albertoventurini.rosiesbooks.platform.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WebFoundationTest {

  @Test
  void redirectsTheRootToTheLibrary() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/reading"));
  }

  @Test
  void headIsSafeAndBodyless() {
    given()
        .redirects()
        .follow(false)
        .when()
        .head("/")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/reading"))
        .body(emptyOrNullString());
  }

  @Test
  void servesTheStylesheetFromTheClasspath() {
    given()
        .when()
        .get("/assets/app.css")
        .then()
        .statusCode(200)
        .contentType("text/css")
        .body(containsString("--color-surface: #f3ede2"))
        .body(containsString("@font-face"));
  }

  @Test
  void servesTheLocalFonts() {
    given()
        .when()
        .get("/assets/fonts/newsreader-latin-v1.woff2")
        .then()
        .statusCode(200)
        .contentType("font/woff2");

    given()
        .when()
        .get("/assets/fonts/ibm-plex-sans-latin-v1.woff2")
        .then()
        .statusCode(200)
        .contentType("font/woff2");
  }

  @Test
  void leavesUnknownRoutesAsNotFound() {
    given()
        .when()
        .get("/not-a-route")
        .then()
        .statusCode(404)
        .header("X-Correlation-ID", nullValue())
        .body(not(containsString("Something went wrong")));
  }
}
