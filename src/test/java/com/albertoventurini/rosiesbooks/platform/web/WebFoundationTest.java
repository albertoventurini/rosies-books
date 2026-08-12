package com.albertoventurini.rosiesbooks.platform.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        .body(containsString("@font-face"))
        .body(containsString(".book-detail {\n  display: grid;\n  align-content: start;"));
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
  void servesThePwaResources() {
    given()
        .when()
        .get("/assets/manifest.webmanifest")
        .then()
        .statusCode(200)
        .contentType("application/manifest+json")
        .body("name", org.hamcrest.Matchers.is("Rosie's Books"))
        .body("short_name", org.hamcrest.Matchers.is("Rosie's Books"))
        .body("start_url", org.hamcrest.Matchers.is("/reading"))
        .body("scope", org.hamcrest.Matchers.is("/"))
        .body("display", org.hamcrest.Matchers.is("standalone"))
        .body("theme_color", org.hamcrest.Matchers.is("#2f4739"))
        .body("background_color", org.hamcrest.Matchers.is("#e7dfd1"));

    given().when().get("/service-worker.js").then().statusCode(200).contentType("text/javascript");
    given().when().get("/offline.html").then().statusCode(200).contentType("text/html");
    given()
        .when()
        .get("/assets/icons/rosies-books-rounded-192.png")
        .then()
        .statusCode(200)
        .contentType("image/png");
    given()
        .when()
        .get("/assets/icons/rosies-books-rounded-512.png")
        .then()
        .statusCode(200)
        .contentType("image/png");
    given()
        .when()
        .get("/assets/icons/rosies-books-rounded-16.png")
        .then()
        .statusCode(200)
        .contentType("image/png");
    given()
        .when()
        .get("/assets/icons/rosies-books-rounded-32.png")
        .then()
        .statusCode(200)
        .contentType("image/png");
    given()
        .when()
        .get("/assets/icons/rosies-books-square-180.png")
        .then()
        .statusCode(200)
        .contentType("image/png");
  }

  @Test
  void sharedShellRegistersThePwa() throws IOException {
    String shell =
        Files.readString(Path.of("src/main/resources/templates/platform/web/shell.html"));

    org.junit.jupiter.api.Assertions.assertAll(
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                shell.contains("/assets/manifest.webmanifest")),
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                shell.contains("rosies-books-rounded-16.png")),
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                shell.contains("rosies-books-rounded-32.png")),
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                shell.contains("rosies-books-square-180.png")),
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                shell.contains("name=\"theme-color\" content=\"#2f4739\"")),
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                shell.contains("/assets/pwa-registration.js")));
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
