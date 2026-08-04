package com.albertoventurini.rosiesbooks.library.web;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BookDeletionResourceTest {

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
    dsl.execute("drop trigger if exists test_web_deletion_failure on user_edition");
    dsl.execute("drop function if exists test_web_deletion_failure()");
  }

  @Test
  void shelfLinksToAnEscapedOwnerScopedConfirmationAndGetAndHeadAreSafe() {
    UUID id = addBook(DevelopmentUser.READER_ONE, "READING", "Delete <private>", 0);

    browser(DevelopmentUser.READER_ONE)
        .get("/reading")
        .then()
        .statusCode(200)
        .body(containsString("href=\"/books/" + id + "/delete\""));
    browser(DevelopmentUser.READER_ONE)
        .get("/books/" + id + "/delete")
        .then()
        .statusCode(200)
        .body(containsString("Delete &lt;private&gt;"))
        .body(not(containsString("Delete <private>")))
        .body(containsString("Current shelf: Reading"))
        .body(containsString("dates, notes, and private metadata will be permanently removed"))
        .body(containsString("name=\"version\" value=\"0\""))
        .body(containsString("name=\"intent\" value=\"delete\""));
    browser(DevelopmentUser.READER_ONE).head("/books/" + id + "/delete").then().statusCode(200);
    org.junit.jupiter.api.Assertions.assertEquals(1, dsl.fetchCount(USER_EDITION));
  }

  @Test
  void cancellationIgnoresAStaleVersionAndSuccessReturnsFixedShelfNotices() {
    UUID cancel = addBook(DevelopmentUser.READER_ONE, "FINISHED", "Cancel", 2);
    post(DevelopmentUser.READER_ONE, cancel.toString(), "cancel", "0")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/finished?notice=book-deletion-cancelled"));
    org.junit.jupiter.api.Assertions.assertEquals(1, dsl.fetchCount(USER_EDITION));

    post(DevelopmentUser.READER_ONE, cancel.toString(), "delete", "2")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/finished?notice=book-deleted"));
    browser(DevelopmentUser.READER_ONE)
        .queryParam("notice", "book-deleted")
        .get("/finished")
        .then()
        .body(containsString("The book was deleted permanently."));
    browser(DevelopmentUser.READER_ONE)
        .queryParam("notice", "book-deletion-cancelled")
        .get("/finished")
        .then()
        .body(containsString("The book deletion was cancelled."));
  }

  @Test
  void staleMalformedRepeatedUnknownAndCrossUserRequestsHaveDeterministicResponses() {
    UUID id = addBook(DevelopmentUser.READER_ONE, "TO_READ", "Protected", 1);
    post(DevelopmentUser.READER_ONE, id.toString(), "delete", "0")
        .then()
        .statusCode(409)
        .body(containsString("changed after this confirmation was opened"))
        .body(containsString("href=\"/books/" + id + "/delete\""));
    post(DevelopmentUser.READER_ONE, id.toString(), "delete", "01").then().statusCode(400);
    post(DevelopmentUser.READER_ONE, id.toString(), "destroy", "1").then().statusCode(400);

    for (String inaccessible : new String[] {id.toString(), UUID.randomUUID().toString(), "bad"}) {
      browser(DevelopmentUser.READER_TWO)
          .get("/books/" + inaccessible + "/delete")
          .then()
          .statusCode(404);
      post(DevelopmentUser.READER_TWO, inaccessible, "delete", "1").then().statusCode(404);
    }
    post(DevelopmentUser.READER_ONE, id.toString(), "delete", "1").then().statusCode(303);
    post(DevelopmentUser.READER_ONE, id.toString(), "delete", "1").then().statusCode(404);
  }

  @Test
  void bothRoutesRequireAuthentication() {
    UUID id = addBook(DevelopmentUser.READER_ONE, "TO_READ", "Auth", 0);
    given().get("/books/" + id + "/delete").then().statusCode(401);
    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("intent", "delete")
        .formParam("version", "0")
        .post("/books/" + id + "/delete")
        .then()
        .statusCode(401);
  }

  @Test
  void persistenceFailureUsesTheSharedCorrelationErrorAndLeavesDeletionRetryable() {
    UUID id = addBook(DevelopmentUser.READER_ONE, "TO_READ", "Retry", 0);
    dsl.execute(
        "create function test_web_deletion_failure() returns trigger language plpgsql as $$"
            + " begin raise exception 'planned web deletion failure'; end $$");
    dsl.execute(
        "create trigger test_web_deletion_failure before delete on user_edition"
            + " for each row execute function test_web_deletion_failure()");

    post(DevelopmentUser.READER_ONE, id.toString(), "delete", "0")
        .then()
        .statusCode(500)
        .header("X-Correlation-ID", not(equalTo("")))
        .body(containsString("We couldn&#39;t complete that request."));
    org.junit.jupiter.api.Assertions.assertEquals(1, dsl.fetchCount(USER_EDITION));
    dropFailureTrigger();
    post(DevelopmentUser.READER_ONE, id.toString(), "delete", "0").then().statusCode(303);
  }

  private static io.restassured.specification.RequestSpecification browser(DevelopmentUser user) {
    return given().redirects().follow(false).cookie("rosies-dev-user", user.alias());
  }

  private static io.restassured.response.Response post(
      DevelopmentUser user, String id, String intent, String version) {
    return browser(user)
        .contentType("application/x-www-form-urlencoded")
        .formParam("intent", intent)
        .formParam("version", version)
        .post("/books/" + id + "/delete");
  }

  private UUID addBook(DevelopmentUser owner, String state, String title, long version) {
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
        .set(
            USER_EDITION.STARTED_ON, state.equals("TO_READ") ? null : LocalDate.parse("2026-07-01"))
        .set(
            USER_EDITION.FINISHED_ON,
            state.equals("FINISHED") ? LocalDate.parse("2026-07-31") : null)
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, title)
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, "Author")
        .set(USER_EDITION.CREATED_AT, timestamp)
        .set(USER_EDITION.UPDATED_AT, timestamp)
        .set(USER_EDITION.VERSION, version)
        .execute();
    return userEditionId;
  }
}
