package com.albertoventurini.rosiesbooks.library.web;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ManualBookResourceTest {

  private static final Pattern REQUEST_ID =
      Pattern.compile("name=\"requestId\" value=\"([^\"]+)\"");

  @Inject DSLContext dsl;

  @BeforeEach
  void cleanLibrary() {
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
  }

  @Test
  void bothDevelopmentUsersOpenACompleteDirectSaveFormWithAnOpaqueRequestId() {
    for (DevelopmentUser user : DevelopmentUser.all()) {
      String body = openForm(user);

      for (String field :
          List.of(
              "requestId",
              "title",
              "authors",
              "subtitle",
              "format",
              "isbn10",
              "isbn13",
              "publisher",
              "publicationDate",
              "pageCount",
              "language",
              "description",
              "state")) {
        assertThat(body, containsString("name=\"" + field + "\""));
      }
      UUID.fromString(requestId(body));
      assertThat(body, containsString("value=\"TO_READ\" selected"));
      assertThat(body, containsString("name=\"intent\" value=\"save\""));
      assertThat(body, containsString("Save book"));
      assertThat(body, not(containsString("Review book")));
      assertThat(body, not(containsString("<script")));
      assertThat(body, containsString(user.displayLabel()));
    }
  }

  @Test
  void everyManualEntryRequestFailsClosedWithoutAResolvedUser() {
    given().when().get("/books/new/manual").then().statusCode(401);
    given().when().head("/books/new/manual").then().statusCode(401).body(emptyOrNullString());
    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("intent", "save")
        .formParam("requestId", UUID.randomUUID())
        .when()
        .post("/books/new/manual")
        .then()
        .statusCode(401);
  }

  @Test
  void formEditingIntentsPreserveTheRequestIdAndEverySubmittedValue() {
    String requestId = requestId(openForm(DevelopmentUser.READER_ONE));
    String added =
        baseForm("add-author", requestId)
            .formParam("title", "A <title>")
            .formParam("authors", "First & Author")
            .formParam("subtitle", "Keep me")
            .when()
            .post("/books/new/manual")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(added, containsString("name=\"requestId\" value=\"" + requestId + "\""));
    assertThat(added, containsString("value=\"A &lt;title&gt;\""));
    assertThat(added, containsString("value=\"First &amp; Author\""));
    assertThat(added, containsString("value=\"Keep me\""));
    assertThat(count(added, "name=\"authors\""), is(2));

    String finished =
        baseForm("change-state", requestId)
            .formParam("title", "A title")
            .formParam("authors", "Author")
            .formParam("state", "FINISHED")
            .formParam("startedOn", "2020-01-02")
            .when()
            .post("/books/new/manual")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(finished, containsString("name=\"requestId\" value=\"" + requestId + "\""));
    assertThat(finished, containsString("name=\"startedOn\" value=\"2020-01-02\""));
    assertThat(finished, containsString("name=\"finishedOn\""));
  }

  @Test
  void invalidSaveRetainsRawValuesRequestIdAndAdjacentErrorsWithoutPersistence() {
    String requestId = UUID.randomUUID().toString();
    String body =
        baseForm("save", requestId)
            .formParam("title", " ")
            .formParam("authors", " ")
            .formParam("isbn10", "wrong")
            .formParam("publicationDate", "2023-02-29<script>")
            .formParam("pageCount", "many")
            .formParam("state", "FINISHED")
            .formParam("startedOn", "2026-08-05")
            .formParam("finishedOn", "2026-08-04")
            .when()
            .post("/books/new/manual")
            .then()
            .statusCode(400)
            .extract()
            .asString();

    for (String field :
        List.of("title", "authors", "isbn10", "publicationDate", "pageCount", "finishedOn")) {
      assertThat(body, containsString("id=\"" + field + "-errors\""));
      assertThat(body, containsString("aria-describedby=\"" + field + "-errors\""));
    }
    assertThat(body, containsString("name=\"requestId\" value=\"" + requestId + "\""));
    assertThat(body, containsString("value=\"2023-02-29&lt;script&gt;\""));
    assertThat(body, not(containsString("2023-02-29<script>")));
    assertThat(dsl.fetchCount(EDITION), is(0));
    assertThat(dsl.fetchCount(USER_EDITION), is(0));
  }

  @Test
  void missingMalformedRequestIdsAndUnsupportedIntentsAreBadRequestsWithoutPersistence() {
    for (String requestId : List.of("", "not-a-uuid")) {
      baseForm("save", requestId)
          .formParam("title", "Valid")
          .formParam("authors", "Author")
          .formParam("state", "TO_READ")
          .when()
          .post("/books/new/manual")
          .then()
          .statusCode(400);
    }
    baseForm("review", UUID.randomUUID().toString())
        .formParam("title", "Valid")
        .formParam("authors", "Author")
        .formParam("state", "TO_READ")
        .when()
        .post("/books/new/manual")
        .then()
        .statusCode(400);
    baseForm("remove-author-nope", UUID.randomUUID().toString())
        .formParam("title", "Valid")
        .formParam("authors", "Author")
        .formParam("state", "TO_READ")
        .when()
        .post("/books/new/manual")
        .then()
        .statusCode(400);
    assertThat(dsl.fetchCount(EDITION), is(0));
    assertThat(dsl.fetchCount(USER_EDITION), is(0));
  }

  @Test
  void validSaveUsesPrgAndMakesTheBookVisibleOnExactlyItsResultingShelf() {
    String requestId = UUID.randomUUID().toString();
    baseForm("save", requestId)
        .redirects()
        .follow(false)
        .formParam("title", "  A saved book  ")
        .formParam("authors", " First Author ", "Second Author")
        .formParam("isbn10", "0-306-40615-2")
        .formParam("state", "READING")
        .formParam("startedOn", "2026-08-01")
        .when()
        .post("/books/new/manual")
        .then()
        .statusCode(303)
        .header("Location", "http://localhost:8081/reading?notice=book-added");

    assertThat(dsl.fetchCount(EDITION), is(1));
    assertThat(dsl.fetchCount(USER_EDITION), is(1));
    given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .when()
        .get("/reading")
        .then()
        .statusCode(200)
        .body(containsString("A saved book"), containsString("First Author, Second Author"));
    for (String route : List.of("/to-read", "/finished")) {
      given()
          .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
          .when()
          .get(route)
          .then()
          .statusCode(200)
          .body(not(containsString("A saved book")));
    }
  }

  @Test
  void repeatingTheSameRequestRedirectsToTheOriginalShelfAndCreatesNothingElse() {
    String requestId = UUID.randomUUID().toString();
    for (int attempt = 0; attempt < 2; attempt++) {
      baseForm("save", requestId)
          .redirects()
          .follow(false)
          .formParam("title", attempt == 0 ? "Original" : "Changed retry")
          .formParam("authors", "Author")
          .formParam("state", attempt == 0 ? "TO_READ" : "FINISHED")
          .formParam("finishedOn", "2026-08-04")
          .when()
          .post("/books/new/manual")
          .then()
          .statusCode(303)
          .header("Location", "http://localhost:8081/to-read?notice=book-added");
    }
    assertThat(dsl.fetchCount(EDITION), is(1));
    assertThat(dsl.fetchCount(USER_EDITION), is(1));
    assertThat(dsl.fetchOne(EDITION).get(EDITION.TITLE), is("Original"));
  }

  @Test
  void getAndHeadAreSafeAndEveryShelfContainsAnOrdinaryManualEntryLink() {
    for (String route : List.of("/reading", "/to-read", "/finished")) {
      given()
          .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
          .when()
          .get(route)
          .then()
          .statusCode(200)
          .body(containsString("href=\"/books/new/manual\""));
    }
    given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .when()
        .head("/books/new/manual")
        .then()
        .statusCode(200)
        .body(emptyOrNullString());
    assertThat(dsl.fetchCount(EDITION), is(0));
    assertThat(dsl.fetchCount(USER_EDITION), is(0));
  }

  private static String openForm(DevelopmentUser user) {
    return given()
        .cookie("rosies-dev-user", user.alias())
        .when()
        .get("/books/new/manual")
        .then()
        .statusCode(200)
        .contentType("text/html; charset=UTF-8")
        .extract()
        .asString();
  }

  private static String requestId(String body) {
    Matcher matcher = REQUEST_ID.matcher(body);
    if (!matcher.find()) {
      throw new AssertionError("No request ID in form");
    }
    return matcher.group(1);
  }

  private static io.restassured.specification.RequestSpecification baseForm(
      String intent, String requestId) {
    return given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .contentType("application/x-www-form-urlencoded")
        .formParam("intent", intent)
        .formParam("requestId", requestId);
  }

  private static int count(String value, String needle) {
    int count = 0;
    int offset = 0;
    while ((offset = value.indexOf(needle, offset)) >= 0) {
      count++;
      offset += needle.length();
    }
    return count;
  }
}
