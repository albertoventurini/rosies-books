package com.albertoventurini.rosiesbooks.library.web;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_ASSET;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BookDetailResourceTest {

  @Inject DSLContext dsl;

  @BeforeEach
  void cleanLibrary() {
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
  }

  @Test
  void rendersAnOwnedBookDetail() {
    UUID edition = UUID.randomUUID();
    UUID book = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    dsl.insertInto(EDITION)
        .set(EDITION.ID, edition)
        .set(EDITION.TITLE, "Detail title")
        .set(EDITION.METADATA_ORIGIN, "MANUAL")
        .set(EDITION.CREATED_AT, now)
        .set(EDITION.UPDATED_AT, now)
        .execute();
    dsl.insertInto(EDITION_AUTHOR)
        .set(EDITION_AUTHOR.EDITION_ID, edition)
        .set(EDITION_AUTHOR.POSITION, 0)
        .set(EDITION_AUTHOR.NAME, "First author")
        .execute();
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, book)
        .set(USER_EDITION.USER_ID, DevelopmentUser.READER_ONE.currentUser().id().value())
        .set(USER_EDITION.EDITION_ID, edition)
        .set(USER_EDITION.STATE, "TO_READ")
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, "Detail title")
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, "First author")
        .set(USER_EDITION.CREATED_AT, now)
        .set(USER_EDITION.UPDATED_AT, now)
        .execute();

    given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .when()
        .get("/books/" + book)
        .then()
        .statusCode(200)
        .body(containsString("Detail title"))
        .body(containsString("class=\"book-detail-placeholder"))
        .body(containsString("href=\"/to-read\""))
        .body(containsString("href=\"/books/" + book + "/state\""))
        .body(containsString("href=\"/books/" + book + "/delete\""))
        .body(not(containsString("/cover\"")));
  }

  @Test
  void returnsStoredCoversOnlyToTheirOwnerAndFailsClosedOtherwise() {
    UUID cover = UUID.randomUUID();
    UUID edition = UUID.randomUUID();
    UUID book = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    dsl.insertInto(COVER_ASSET)
        .set(COVER_ASSET.ID, cover)
        .set(COVER_ASSET.CONTENT, new byte[] {1, 2, 3})
        .set(COVER_ASSET.MIME_TYPE, "image/png")
        .execute();
    dsl.insertInto(EDITION)
        .set(EDITION.ID, edition)
        .set(EDITION.TITLE, "Covered")
        .set(EDITION.COVER_ASSET_ID, cover)
        .set(EDITION.METADATA_ORIGIN, "MANUAL")
        .set(EDITION.CREATED_AT, now)
        .set(EDITION.UPDATED_AT, now)
        .execute();
    dsl.insertInto(EDITION_AUTHOR)
        .set(EDITION_AUTHOR.EDITION_ID, edition)
        .set(EDITION_AUTHOR.POSITION, 0)
        .set(EDITION_AUTHOR.NAME, "Author")
        .execute();
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, book)
        .set(USER_EDITION.USER_ID, DevelopmentUser.READER_ONE.currentUser().id().value())
        .set(USER_EDITION.EDITION_ID, edition)
        .set(USER_EDITION.STATE, "TO_READ")
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, "Covered")
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, "Author")
        .set(USER_EDITION.CREATED_AT, now)
        .set(USER_EDITION.UPDATED_AT, now)
        .execute();

    byte[] returned =
        given()
            .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
            .when()
            .get("/books/" + book + "/cover")
            .then()
            .statusCode(200)
            .contentType("image/png")
            .header("Cache-Control", containsString("no-store"))
            .extract()
            .asByteArray();
    assertArrayEquals(new byte[] {1, 2, 3}, returned);
    given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_TWO.alias())
        .when()
        .get("/books/" + book + "/cover")
        .then()
        .statusCode(404);
    given().when().get("/books/" + book).then().statusCode(401);
    given()
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .when()
        .get("/books/not-a-uuid")
        .then()
        .statusCode(404);
  }
}
