package com.albertoventurini.rosiesbooks.platform.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WebFoundationTest {

  @Test
  void rendersTheFoundationPageInTheSharedShell() {
    given()
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .contentType("text/html; charset=UTF-8")
        .body(containsString("<title>Rosie&#39;s books</title>"))
        .body(
            containsString(
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"))
        .body(containsString("<link rel=\"stylesheet\" href=\"/assets/app.css\">"))
        .body(containsString("<header class=\"site-header\">"))
        .body(containsString("<main class=\"page-content\" id=\"main-content\">"))
        .body(containsString("<h1>Rosie&#39;s books</h1>"))
        .body(containsString("A quiet, private place to keep track of your reading."));
  }

  @Test
  void headIsSafeAndBodyless() {
    given()
        .when()
        .head("/")
        .then()
        .statusCode(200)
        .contentType("text/html; charset=UTF-8")
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
  void escapesUntrustedViewModelText() {
    String html =
        WebTemplates.foundation(
                new FoundationPage(
                    "<script>alert('title')</script>", "<img src=x onerror=private>"))
            .render();

    assertThat(html, containsString("&lt;script&gt;alert(&#39;title&#39;)&lt;/script&gt;"));
    assertThat(html, containsString("&lt;img src=x onerror=private&gt;"));
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
