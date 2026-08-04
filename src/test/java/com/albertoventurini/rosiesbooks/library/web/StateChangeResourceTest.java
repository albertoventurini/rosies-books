package com.albertoventurini.rosiesbooks.library.web;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StateChangeResourceTest {

  @Inject DSLContext dsl;

  @BeforeEach
  void cleanLibrary() {
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
  }

  @Test
  void shelfLinksToOwnedStateFormAndAllNonConfirmationTransitionsUpdateTheSameRow() {
    UUID toReading = addBook(DevelopmentUser.READER_ONE, "TO_READ", null, null, "To reading");
    given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .get("/to-read")
        .then()
        .statusCode(200)
        .body(containsString("href=\"/books/" + toReading + "/state\""));
    change(toReading, 0, "READING", null, null).statusCode(303);
    assertState(toReading, "READING", LocalDate.now(), null, 1);

    UUID toFinished = addBook(DevelopmentUser.READER_ONE, "TO_READ", null, null, "To finished");
    change(toFinished, 0, "FINISHED", "2026-07-01", "2026-08-02").statusCode(303);
    assertState(toFinished, "FINISHED", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 2), 1);

    UUID readingFinished =
        addBook(
            DevelopmentUser.READER_ONE,
            "READING",
            LocalDate.of(2026, 6, 3),
            null,
            "Reading finished");
    change(readingFinished, 0, "FINISHED", null, "2026-08-03").statusCode(303);
    assertState(readingFinished, "FINISHED", LocalDate.of(2026, 6, 3), LocalDate.of(2026, 8, 3), 1);

    UUID finishedReading =
        addBook(
            DevelopmentUser.READER_ONE,
            "FINISHED",
            LocalDate.of(2026, 5, 2),
            LocalDate.of(2026, 5, 9),
            "Finished reading");
    change(finishedReading, 0, "READING", null, null).statusCode(303);
    assertState(finishedReading, "READING", LocalDate.of(2026, 5, 2), null, 1);

    UUID finishedUnknownStart =
        addBook(
            DevelopmentUser.READER_ONE,
            "FINISHED",
            null,
            LocalDate.of(2026, 5, 9),
            "Unknown start");
    change(finishedUnknownStart, 0, "READING", null, null).statusCode(303);
    assertState(finishedUnknownStart, "READING", LocalDate.now(), null, 1);
  }

  @Test
  void movesToReadOnlyAfterExactDateConfirmationAndCancellationDoesNotMutate() {
    UUID id =
        addBook(
            DevelopmentUser.READER_ONE,
            "FINISHED",
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 3, 4),
            "Dates <must> be escaped");

    String confirmation = change(id, 0, "TO_READ", null, null).statusCode(200).extract().asString();
    assertThat(confirmation, containsString("2026-01-02"));
    assertThat(confirmation, containsString("2026-03-04"));
    assertThat(confirmation, containsString("Dates &lt;must&gt; be escaped"));
    assertState(id, "FINISHED", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 3, 4), 0);

    post(id, "cancel", "0", "TO_READ", null, null)
        .then()
        .statusCode(303)
        .header(
            "Location", org.hamcrest.Matchers.endsWith("/finished?notice=state-change-cancelled"));
    assertState(id, "FINISHED", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 3, 4), 0);

    post(id, "confirm", "0", "TO_READ", null, null)
        .then()
        .statusCode(303)
        .header("Location", org.hamcrest.Matchers.endsWith("/to-read?notice=state-changed"));
    assertState(id, "TO_READ", null, null, 1);

    UUID reading =
        addBook(
            DevelopmentUser.READER_ONE,
            "READING",
            LocalDate.of(2026, 2, 3),
            null,
            "Reading to read");
    change(reading, 0, "TO_READ", null, null).statusCode(200);
    post(reading, "confirm", "0", "TO_READ", null, null).then().statusCode(303);
    assertState(reading, "TO_READ", null, null, 1);
  }

  @Test
  void staleAndRepeatedFormsConflictWithoutFurtherMutation() {
    UUID id = addBook(DevelopmentUser.READER_ONE, "TO_READ", null, null, "Concurrency");
    change(id, 0, "READING", null, null).statusCode(303);

    change(id, 0, "READING", null, null)
        .statusCode(409)
        .body(containsString("changed after this form was opened"));
    assertState(id, "READING", LocalDate.now(), null, 1);
  }

  @Test
  void malformedInputsAreRetainedAndDoNotMutate() {
    UUID id = addBook(DevelopmentUser.READER_ONE, "TO_READ", null, null, "Validation");
    String body =
        post(id, "change", "0", "FINISHED", "2026-08-05", "bad<script>")
            .then()
            .statusCode(400)
            .extract()
            .asString();
    assertThat(body, containsString("value=\"bad&lt;script&gt;\""));
    assertThat(body, not(containsString("value=\"bad<script>\"")));
    assertThat(body, containsString("id=\"finishedOn-errors\""));
    assertState(id, "TO_READ", null, null, 0);
  }

  @Test
  void unknownMalformedAndCrossUserRequestsAreIndistinguishableAndCannotMutate() {
    UUID id = addBook(DevelopmentUser.READER_ONE, "TO_READ", null, null, "Private");
    for (String inaccessible : new String[] {id.toString(), UUID.randomUUID().toString(), "bad"}) {
      given()
          .cookie("rosies-dev-user", DevelopmentUser.READER_TWO.alias())
          .get("/books/" + inaccessible + "/state")
          .then()
          .statusCode(404);
      postAs(DevelopmentUser.READER_TWO, inaccessible, "cancel", "0", "READING", null, null)
          .then()
          .statusCode(404);
    }
    assertState(id, "TO_READ", null, null, 0);
  }

  @Test
  void onlyRecognizedNoticesRenderTheTransientShelfEnhancement() {
    given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .queryParam("notice", "state-changed")
        .get("/reading")
        .then()
        .statusCode(200)
        .body(containsString("data-transient-notice"))
        .body(containsString("/assets/shelf-notice.js"));
    given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .queryParam("notice", "validation-error")
        .get("/reading")
        .then()
        .statusCode(200)
        .body(not(containsString("data-transient-notice")))
        .body(not(containsString("/assets/shelf-notice.js")));
  }

  private io.restassured.response.ValidatableResponse change(
      UUID id, long version, String target, String started, String finished) {
    return post(id, "change", Long.toString(version), target, started, finished).then();
  }

  private Response post(
      UUID id, String intent, String version, String target, String started, String finished) {
    return postAs(
        DevelopmentUser.READER_ONE, id.toString(), intent, version, target, started, finished);
  }

  private Response postAs(
      DevelopmentUser user,
      String id,
      String intent,
      String version,
      String target,
      String started,
      String finished) {
    RequestSpecification request =
        given()
            .redirects()
            .follow(false)
            .cookie("rosies-dev-user", user.alias())
            .contentType("application/x-www-form-urlencoded")
            .formParam("intent", intent)
            .formParam("version", version)
            .formParam("target", target);
    if (started != null) request.formParam("startedOn", started);
    if (finished != null) request.formParam("finishedOn", finished);
    return request.when().post("/books/" + id + "/state");
  }

  private UUID addBook(
      DevelopmentUser owner,
      String state,
      LocalDate startedOn,
      LocalDate finishedOn,
      String title) {
    UUID editionId = UUID.randomUUID();
    UUID userEditionId = UUID.randomUUID();
    var timestamp = Instant.parse("2026-08-01T10:00:00Z").atOffset(ZoneOffset.UTC);
    dsl.insertInto(EDITION)
        .set(EDITION.ID, editionId)
        .set(EDITION.TITLE, title)
        .set(EDITION.METADATA_ORIGIN, "MANUAL")
        .set(EDITION.CREATED_AT, timestamp)
        .set(EDITION.UPDATED_AT, timestamp)
        .execute();
    dsl.insertInto(EDITION_AUTHOR)
        .set(EDITION_AUTHOR.EDITION_ID, editionId)
        .set(EDITION_AUTHOR.POSITION, 0)
        .set(EDITION_AUTHOR.NAME, "Author")
        .execute();
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, userEditionId)
        .set(USER_EDITION.USER_ID, owner.currentUser().id().value())
        .set(USER_EDITION.EDITION_ID, editionId)
        .set(USER_EDITION.STATE, state)
        .set(USER_EDITION.STARTED_ON, startedOn)
        .set(USER_EDITION.FINISHED_ON, finishedOn)
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, title)
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, "Author")
        .set(USER_EDITION.CREATED_AT, timestamp)
        .set(USER_EDITION.UPDATED_AT, timestamp)
        .set(USER_EDITION.VERSION, 0L)
        .execute();
    return userEditionId;
  }

  private void assertState(
      UUID id, String state, LocalDate startedOn, LocalDate finishedOn, long version) {
    var row =
        dsl.select(
                USER_EDITION.STATE,
                USER_EDITION.STARTED_ON,
                USER_EDITION.FINISHED_ON,
                USER_EDITION.VERSION)
            .from(USER_EDITION)
            .where(USER_EDITION.ID.eq(id))
            .fetchSingle();
    assertThat(row.value1(), is(state));
    assertThat(row.value2(), is(startedOn));
    assertThat(row.value3(), is(finishedOn));
    assertThat(row.value4(), is(version));
  }
}
