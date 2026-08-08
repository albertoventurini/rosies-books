package com.albertoventurini.rosiesbooks.library.web;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ShelfResourceTest {

  @Inject DSLContext dsl;

  @BeforeEach
  void cleanLibrary() {
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
  }

  @Test
  void rootRedirectsToReadingForGetAndHead() {
    for (String method : List.of("GET", "HEAD")) {
      given()
          .redirects()
          .follow(false)
          .when()
          .request(method, "/")
          .then()
          .statusCode(303)
          .header("Location", endsWith("/reading"));
    }
  }

  @Test
  void eachSelectedDevelopmentUserCanOpenEveryShelfWithoutJavaScript() {
    for (DevelopmentUser user : DevelopmentUser.all()) {
      for (String route : List.of("/reading", "/to-read", "/finished")) {
        given()
            .cookie("rosies-dev-user", user.alias())
            .when()
            .get(route)
            .then()
            .statusCode(200)
            .contentType("text/html; charset=UTF-8")
            .body(
                containsString(
                    "<span class=\"current-user-label\">" + user.displayLabel() + "</span>"))
            .body(containsString("href=\"/reading\""))
            .body(containsString("href=\"/to-read\""))
            .body(containsString("href=\"/finished\""))
            .body(not(containsString("<script")));

        given()
            .cookie("rosies-dev-user", user.alias())
            .when()
            .head(route)
            .then()
            .statusCode(200)
            .contentType("text/html; charset=UTF-8")
            .body(emptyOrNullString());
      }
    }
  }

  @Test
  void shelfRequestsWithoutAResolvedUserFailClosed() {
    for (String route : List.of("/reading", "/to-read", "/finished")) {
      given().when().get(route).then().statusCode(401);
      given().when().head(route).then().statusCode(401).body(emptyOrNullString());
    }
  }

  @Test
  void marksOnlyTheCurrentNavigationLinkAndShowsShelfSpecificEmptyStates() {
    assertActiveAndEmpty("/reading", "Reading", "No books are currently being read.");
    assertActiveAndEmpty("/to-read", "To Read", "There are no books waiting to be read.");
    String finished =
        given()
            .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
            .when()
            .get("/finished")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    assertThat(finished, containsString("0 books read in 2026"));
    assertThat(finished, containsString("No books finished in 2026."));
    assertThat(finished, containsString("href=\"/finished?year=2026\""));
    assertThat(finished, containsString("aria-current=\"true\""));
    assertThat(finished, containsString("Add book manually"));
  }

  @Test
  void rendersOnlyOwnerDataInShelfOrderAndEscapesAllMetadata() {
    UUID older =
        addBook(
            DevelopmentUser.READER_ONE,
            "READING",
            "Older & <script>alert('title')</script>",
            List.of("First <author>", "Second & author"),
            LocalDate.of(2026, 7, 1),
            Instant.parse("2026-07-01T00:00:00Z"));
    addBook(
        DevelopmentUser.READER_ONE,
        "READING",
        "Newer title",
        List.of("Newer author"),
        LocalDate.of(2026, 8, 1),
        Instant.parse("2026-08-01T00:00:00Z"));
    addBook(
        DevelopmentUser.READER_TWO,
        "READING",
        "Other user's secret title",
        List.of("Secret author"),
        LocalDate.of(2026, 8, 2),
        Instant.parse("2026-08-02T00:00:00Z"));

    String body =
        given()
            .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
            .when()
            .get("/reading")
            .then()
            .statusCode(200)
            .extract()
            .asString();

    assertThat(
        body.indexOf("Newer title"), org.hamcrest.Matchers.lessThan(body.indexOf("Older &amp;")));
    assertThat(
        body, containsString("Older &amp; &lt;script&gt;alert(&#39;title&#39;)&lt;/script&gt;"));
    assertThat(body, containsString("First &lt;author&gt;, Second &amp; author"));
    assertThat(body, not(containsString("Other user's secret title")));
    assertThat(body, containsString("href=\"/books/" + older + "/state\""));
    assertThat(body, containsString("href=\"/books/" + older + "\""));
    for (DevelopmentUser user : DevelopmentUser.all()) {
      assertThat(body, not(containsString(user.currentUser().id().value().toString())));
      assertThat(body, not(containsString(user.email())));
      assertThat(body, not(containsString(user.oidcSubject())));
    }
    assertThat(body, not(containsString(DevelopmentUser.OIDC_ISSUER)));
    assertThat(body, not(containsString("style=")));
    assertThat(body, containsString("placeholder-theme-"));
    assertThat(body, containsString("class=\"shelf-book-card\""));
    assertThat(body, containsString("<span class=\"shelf-book-state\">Reading</span>"));
    assertThat(body, containsString("Started 1 Jul 2026"));
    assertThat(body, containsString("href=\"/books/" + older + "/delete\""));
  }

  @Test
  void filtersFinishedByYearAndRendersOrdinaryYearLinksAndMatchingCounts() {
    addBook(
        DevelopmentUser.READER_ONE,
        "READING",
        "Older reading",
        List.of("Author"),
        LocalDate.of(2026, 1, 1),
        Instant.parse("2026-02-01T00:00:00Z"));
    addBook(
        DevelopmentUser.READER_ONE,
        "READING",
        "Newer reading",
        List.of("Author"),
        LocalDate.of(2026, 2, 1),
        Instant.parse("2026-01-01T00:00:00Z"));
    addBook(
        DevelopmentUser.READER_ONE,
        "TO_READ",
        "Older to read",
        List.of("Author"),
        LocalDate.of(2026, 1, 1),
        Instant.parse("2026-01-01T00:00:00Z"));
    addBook(
        DevelopmentUser.READER_ONE,
        "TO_READ",
        "Newer to read",
        List.of("Author"),
        LocalDate.of(2026, 1, 1),
        Instant.parse("2026-02-01T00:00:00Z"));
    addBook(
        DevelopmentUser.READER_ONE,
        "FINISHED",
        "Finished in 2024",
        List.of("Author"),
        LocalDate.of(2024, 12, 31),
        Instant.parse("2026-02-01T00:00:00Z"));
    addBook(
        DevelopmentUser.READER_ONE,
        "FINISHED",
        "Finished in 2026",
        List.of("Author"),
        LocalDate.of(2026, 1, 1),
        Instant.parse("2026-01-01T00:00:00Z"));
    addBook(
        DevelopmentUser.READER_ONE,
        "FINISHED",
        "Finished later in 2026",
        List.of("Author"),
        LocalDate.of(2026, 2, 1),
        Instant.parse("2026-02-01T00:00:00Z"));

    String reading = assertBookOrder("/reading", "Newer reading", "Older reading");
    assertThat(reading, containsString("Reading</span> · Started 1 Feb 2026"));
    String toRead = assertBookOrder("/to-read", "Newer to read", "Older to read");
    assertThat(toRead, containsString("To Read</span> · Added "));
    String finished =
        assertBookOrder("/finished?year=2026", "Finished later in 2026", "Finished in 2026");
    assertThat(finished, containsString("Finished</span> · Finished 1 Jan 2026"));
    assertThat(finished, not(containsString("Finished in 2024")));
    assertThat(finished, containsString("2 books read in 2026"));
    assertThat(finished, containsString("href=\"/finished?year=2024\""));

    String older = assertBookOrder("/finished?year=2024", "Finished in 2024");
    assertThat(older, containsString("1 book read in 2024"));
    assertThat(older, not(containsString("Finished in 2026")));
    assertThat(older, not(containsString("<script")));

    assertThat(reading, not(containsString("books read in")));
    assertThat(reading, not(containsString("/finished?year=")));
    assertThat(toRead, not(containsString("books read in")));
    assertThat(toRead, not(containsString("/finished?year=")));
  }

  @Test
  void rejectsMalformedAndUnavailableFinishedYearsWithoutLeakingAnotherOwnersYears() {
    addBook(
        DevelopmentUser.READER_TWO,
        "FINISHED",
        "Other user's old finish",
        List.of("Secret"),
        LocalDate.of(2024, 1, 1),
        Instant.parse("2024-01-01T00:00:00Z"));

    for (String year : List.of("not-a-year", "2024", "2026-01")) {
      given()
          .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
          .queryParam("year", year)
          .when()
          .get("/finished")
          .then()
          .statusCode(400)
          .body(not(containsString("Other user's old finish")))
          .body(not(containsString("2024 books")));
    }
  }

  @Test
  void escapesTheCurrentUserDisplayLabelInTheCheckedTemplate() {
    String html =
        ShelfTemplates.shelf(
                ShelfPage.from(
                    "<img src=x onerror=private>",
                    Shelf.READING,
                    List.of(),
                    LocalDate.of(2026, 8, 4),
                    ZoneId.of("Africa/Johannesburg")))
            .render();

    assertThat(html, containsString("&lt;img src=x onerror=private&gt;"));
    assertThat(html, not(containsString("<img src=x")));
  }

  private void assertActiveAndEmpty(String route, String heading, String emptyMessage) {
    String body =
        given()
            .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
            .when()
            .get(route)
            .then()
            .statusCode(200)
            .extract()
            .asString();

    assertThat(body, containsString("<h1>" + heading + "</h1>"));
    assertThat(body, containsString("href=\"" + route + "\" aria-current=\"page\""));
    assertThat(body, containsString(emptyMessage));
    assertThat(body, not(containsString("Add Book")));
    assertThat(body, not(containsString("type=\"module\"")));
  }

  private String assertBookOrder(String route, String... expected) {
    String body =
        given()
            .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
            .when()
            .get(route)
            .then()
            .statusCode(200)
            .extract()
            .asString();
    for (int index = 1; index < expected.length; index++) {
      assertThat(
          body.indexOf(expected[index - 1]),
          org.hamcrest.Matchers.lessThan(body.indexOf(expected[index])));
    }
    return body;
  }

  private UUID addBook(
      DevelopmentUser owner,
      String state,
      String title,
      List<String> authors,
      LocalDate stateDate,
      Instant createdAt) {
    UUID editionId = UUID.randomUUID();
    UUID userEditionId = UUID.randomUUID();
    var timestamp = createdAt.atOffset(ZoneOffset.UTC);
    dsl.insertInto(EDITION)
        .set(EDITION.ID, editionId)
        .set(EDITION.TITLE, title)
        .set(EDITION.METADATA_ORIGIN, "MANUAL")
        .set(EDITION.CREATED_AT, timestamp)
        .set(EDITION.UPDATED_AT, timestamp)
        .execute();
    for (int position = 0; position < authors.size(); position++) {
      dsl.insertInto(EDITION_AUTHOR)
          .set(EDITION_AUTHOR.EDITION_ID, editionId)
          .set(EDITION_AUTHOR.POSITION, position)
          .set(EDITION_AUTHOR.NAME, authors.get(position))
          .execute();
    }
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, userEditionId)
        .set(USER_EDITION.USER_ID, owner.currentUser().id().value())
        .set(USER_EDITION.EDITION_ID, editionId)
        .set(USER_EDITION.STATE, state)
        .set(USER_EDITION.STARTED_ON, state.equals("READING") ? stateDate : null)
        .set(USER_EDITION.FINISHED_ON, state.equals("FINISHED") ? stateDate : null)
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, title)
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, String.join(" ", authors))
        .set(USER_EDITION.CREATED_AT, timestamp)
        .set(USER_EDITION.UPDATED_AT, timestamp)
        .execute();
    return userEditionId;
  }
}
