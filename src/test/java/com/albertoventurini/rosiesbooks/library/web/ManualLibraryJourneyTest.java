package com.albertoventurini.rosiesbooks.library.web;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ManualLibraryJourneyTest {

  @Inject DSLContext dsl;

  @BeforeEach
  void cleanLibrary() {
    dropFailureTrigger();
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
  }

  @AfterEach
  void dropFailureTrigger() {
    dsl.execute("drop trigger if exists test_journey_deletion_failure on user_edition");
    dsl.execute("drop function if exists test_journey_deletion_failure()");
  }

  @Test
  void completesThePostgresBackedManualLibraryJourneyWithoutProviderOrLiveOidc() {
    UUID toReadRequest = UUID.randomUUID();
    add(toReadRequest, "Journey To Read", "TO_READ", null, null).then().statusCode(303);
    add(UUID.randomUUID(), "Journey Reading", "READING", "2026-07-01", null).then().statusCode(303);
    add(UUID.randomUUID(), "Journey Finished", "FINISHED", "2026-06-01", "2026-06-30")
        .then()
        .statusCode(303);

    add(toReadRequest, "Changed replay", "FINISHED", null, "2026-08-04").then().statusCode(303);
    assertEquals(3, dsl.fetchCount(EDITION));
    assertEquals(3, dsl.fetchCount(USER_EDITION));
    assertExactlyOneShelf("Journey To Read", "/to-read");
    assertExactlyOneShelf("Journey Reading", "/reading");
    assertExactlyOneShelf("Journey Finished", "/finished");

    UUID toRead = id("Journey To Read");
    state(toRead, "change", "0", "READING", "2026-08-01", null).then().statusCode(303);
    UUID reading = id("Journey Reading");
    state(reading, "change", "0", "TO_READ", null, null).then().statusCode(200);
    state(reading, "confirm", "0", "TO_READ", null, null).then().statusCode(303);
    state(reading, "confirm", "0", "TO_READ", null, null).then().statusCode(409);
    assertEquals(3, dsl.fetchCount(USER_EDITION));

    UUID finished = id("Journey Finished");
    delete(DevelopmentUser.READER_ONE, finished, "cancel", "99").then().statusCode(303);
    delete(DevelopmentUser.READER_TWO, finished, "delete", "0").then().statusCode(404);
    assertEquals(3, dsl.fetchCount(USER_EDITION));

    installFailureTrigger();
    delete(DevelopmentUser.READER_ONE, finished, "delete", "0").then().statusCode(500);
    assertEquals(3, dsl.fetchCount(USER_EDITION));
    dropFailureTrigger();
    delete(DevelopmentUser.READER_ONE, finished, "delete", "0").then().statusCode(303);
    delete(DevelopmentUser.READER_ONE, finished, "delete", "0").then().statusCode(404);

    delete(DevelopmentUser.READER_ONE, toRead, "delete", "1").then().statusCode(303);
    assertEquals(1, dsl.fetchCount(USER_EDITION));
    assertEquals(1, dsl.fetchCount(EDITION));
    browser(DevelopmentUser.READER_ONE)
        .get("/to-read")
        .then()
        .body(containsString("Journey Reading"))
        .body(not(containsString("Journey Finished")))
        .body(not(containsString("Journey To Read")));
  }

  private Response add(
      UUID requestId, String title, String state, String startedOn, String finishedOn) {
    RequestSpecification request =
        browser(DevelopmentUser.READER_ONE)
            .contentType("application/x-www-form-urlencoded")
            .formParam("intent", "save")
            .formParam("requestId", requestId)
            .formParam("title", title)
            .formParam("authors", "Journey Author")
            .formParam("state", state);
    if (startedOn != null) request.formParam("startedOn", startedOn);
    if (finishedOn != null) request.formParam("finishedOn", finishedOn);
    return request.post("/books/new/manual");
  }

  private static Response state(
      UUID id, String intent, String version, String target, String startedOn, String finishedOn) {
    RequestSpecification request =
        browser(DevelopmentUser.READER_ONE)
            .contentType("application/x-www-form-urlencoded")
            .formParam("intent", intent)
            .formParam("version", version)
            .formParam("target", target);
    if (startedOn != null) request.formParam("readingStartedOn", startedOn);
    if (finishedOn != null) request.formParam("finishedOn", finishedOn);
    return request.post("/books/" + id + "/state");
  }

  private static Response delete(DevelopmentUser user, UUID id, String intent, String version) {
    return browser(user)
        .contentType("application/x-www-form-urlencoded")
        .formParam("intent", intent)
        .formParam("version", version)
        .post("/books/" + id + "/delete");
  }

  private void assertExactlyOneShelf(String title, String expectedRoute) {
    for (String route : List.of("/to-read", "/reading", "/finished")) {
      var assertion = browser(DevelopmentUser.READER_ONE).get(route).then().statusCode(200);
      if (route.equals(expectedRoute)) assertion.body(containsString(title));
      else assertion.body(not(containsString(title)));
    }
  }

  private UUID id(String title) {
    return dsl.select(USER_EDITION.ID)
        .from(USER_EDITION)
        .where(USER_EDITION.EFFECTIVE_TITLE_SEARCH.eq(title))
        .fetchSingle(USER_EDITION.ID);
  }

  private void installFailureTrigger() {
    dsl.execute(
        "create function test_journey_deletion_failure() returns trigger language plpgsql as $$"
            + " begin raise exception 'planned journey failure'; end $$");
    dsl.execute(
        "create trigger test_journey_deletion_failure after delete on user_edition"
            + " for each row execute function test_journey_deletion_failure()");
  }

  private static RequestSpecification browser(DevelopmentUser user) {
    return given().redirects().follow(false).cookie("rosies-dev-user", user.alias());
  }
}
