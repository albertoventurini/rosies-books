package com.albertoventurini.rosiesbooks.library.web;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ManualBookResourceTest {

  @Inject DSLContext dsl;

  @BeforeEach
  void cleanLibrary() {
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
  }

  @Test
  void bothDevelopmentUsersCanOpenTheCompleteDefaultFormWithoutJavaScript() {
    for (DevelopmentUser user : DevelopmentUser.all()) {
      String body =
          given()
              .cookie("rosies-dev-user", user.alias())
              .when()
              .get("/books/new/manual")
              .then()
              .statusCode(200)
              .contentType("text/html; charset=UTF-8")
              .extract()
              .asString();

      for (String field :
          List.of(
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
      assertThat(body, containsString("value=\"TO_READ\" selected"));
      assertThat(body, not(containsString("name=\"startedOn\"")));
      assertThat(body, not(containsString("name=\"finishedOn\"")));
      assertThat(body, containsString("Review book"));
      assertThat(body, not(containsString(">Save<")));
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
        .formParam("intent", "review")
        .when()
        .post("/books/new/manual")
        .then()
        .statusCode(401);
  }

  @Test
  void authorAndStateIntentsPreserveAllValuesAndRenderOnlyAllowedDates() {
    String added =
        baseForm("add-author")
            .formParam("title", "A <title>")
            .formParam("authors", "First & Author")
            .formParam("subtitle", "Keep me")
            .when()
            .post("/books/new/manual")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(added, containsString("value=\"A &lt;title&gt;\""));
    assertThat(added, containsString("value=\"First &amp; Author\""));
    assertThat(added, containsString("value=\"Keep me\""));
    assertThat(count(added, "name=\"authors\""), org.hamcrest.Matchers.is(2));

    String reading =
        baseForm("change-state")
            .formParam("title", "A title")
            .formParam("authors", "Author")
            .formParam("state", "READING")
            .formParam("finishedOn", "1999-01-01")
            .when()
            .post("/books/new/manual")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(reading, containsString("name=\"startedOn\""));
    assertThat(reading, not(containsString("name=\"finishedOn\"")));

    String finished =
        baseForm("change-state")
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
    assertThat(finished, containsString("name=\"startedOn\" value=\"2020-01-02\""));
    assertThat(finished, containsString("name=\"finishedOn\""));
  }

  @Test
  void invalidReviewAggregatesAdjacentAccessibleErrorsAndRetainsRawInput() {
    String body =
        baseForm("review")
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
    assertThat(body, containsString("value=\"2023-02-29&lt;script&gt;\""));
    assertThat(body, not(containsString("2023-02-29<script>")));
  }

  @Test
  void validReviewIsEscapedNormalizedExplicitlyNonPersistingAndCanReturnToEdit() {
    String review =
        baseForm("review")
            .formParam("title", "  A <book>  ")
            .formParam("authors", " First & Author ", " ", "Second")
            .formParam("isbn10", "0-306-40615-2")
            .formParam("publicationDate", "2024-02")
            .formParam("pageCount", "321")
            .formParam("state", "FINISHED")
            .formParam("finishedOn", "2026-08-04")
            .when()
            .post("/books/new/manual")
            .then()
            .statusCode(200)
            .extract()
            .asString();

    assertThat(review, containsString("Review manual book"));
    assertThat(review, containsString("Nothing has been saved"));
    assertThat(review, containsString("A &lt;book&gt;"));
    assertThat(review, containsString("First &amp; Author"));
    assertThat(
        review.indexOf("First &amp; Author"),
        org.hamcrest.Matchers.lessThan(review.indexOf("Second")));
    assertThat(review, containsString("0306406152"));
    assertThat(review, containsString("9780306406157"));
    assertThat(review, containsString("name=\"intent\" value=\"edit\""));
    assertThat(review, not(containsString("<script")));
    assertThat(dsl.fetchCount(EDITION), org.hamcrest.Matchers.is(0));
    assertThat(dsl.fetchCount(USER_EDITION), org.hamcrest.Matchers.is(0));

    String edit =
        baseForm("edit")
            .formParam("title", "A <book>")
            .formParam("authors", "First & Author", "Second")
            .formParam("state", "TO_READ")
            .when()
            .post("/books/new/manual")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(edit, containsString("value=\"A &lt;book&gt;\""));
    assertThat(edit, containsString("value=\"First &amp; Author\""));
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
    assertThat(dsl.fetchCount(EDITION), org.hamcrest.Matchers.is(0));
    assertThat(dsl.fetchCount(USER_EDITION), org.hamcrest.Matchers.is(0));
  }

  private static io.restassured.specification.RequestSpecification baseForm(String intent) {
    return given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .contentType("application/x-www-form-urlencoded")
        .formParam("intent", intent);
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
