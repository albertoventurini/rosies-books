package com.albertoventurini.rosiesbooks.library.imports;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GoodreadsImportResourceTest {

  @Inject DSLContext dsl;

  @BeforeEach
  void cleanImports() {
    dsl.execute("truncate table cover_fetch_task, goodreads_import");
  }

  @Test
  void listsOnlyTheCurrentUsersSuccessfulImportsWithLinksToTheirResults() {
    UUID readerOneImport = successfulImport(DevelopmentUser.READER_ONE, 3, 1);
    UUID anotherReaderOneImport = successfulImport(DevelopmentUser.READER_ONE, 1, 2);
    UUID readerTwoImport = successfulImport(DevelopmentUser.READER_TWO, 2, 0);

    browser(DevelopmentUser.READER_ONE)
        .get("/imports/goodreads")
        .then()
        .statusCode(200)
        .body(containsString("Please select a Goodreads CSV file"))
        .body(containsString("Previous imports"))
        .body(containsString("3 imported, 1 already present"))
        .body(containsString("href=\"/imports/goodreads/" + readerOneImport + "\""))
        .body(containsString("href=\"/imports/goodreads/" + anotherReaderOneImport + "\""))
        .body(not(containsString(readerTwoImport.toString())));

    browser(DevelopmentUser.READER_TWO)
        .get("/imports/goodreads")
        .then()
        .statusCode(200)
        .body(containsString("href=\"/imports/goodreads/" + readerTwoImport + "\""))
        .body(not(containsString(readerOneImport.toString())))
        .body(not(containsString(anotherReaderOneImport.toString())));
  }

  private UUID successfulImport(DevelopmentUser user, int imported, int alreadyPresent) {
    UUID requestId = UUID.randomUUID();
    dsl.execute(
        "insert into goodreads_import (request_id, user_id, imported_count, already_present_count,"
            + " reading_count, to_read_count, finished_count, created_at)"
            + " values (?, ?, ?, ?, 0, ?, 0, current_timestamp)",
        requestId,
        user.currentUser().id().value(),
        imported,
        alreadyPresent,
        imported);
    return requestId;
  }

  private static io.restassured.specification.RequestSpecification browser(DevelopmentUser user) {
    return given().cookie("rosies-dev-user", user.alias());
  }
}
