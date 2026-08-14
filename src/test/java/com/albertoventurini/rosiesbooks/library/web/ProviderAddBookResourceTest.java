package com.albertoventurini.rosiesbooks.library.web;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import com.albertoventurini.rosiesbooks.provider.api.Isbn13;
import com.albertoventurini.rosiesbooks.provider.api.SelectedEdition;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProviderAddBookResourceTest {

  @Inject ProviderReviewToken tokens;

  @Test
  void acceptsAReviewTokenContainingAProviderDescriptionLargerThanTheDefaultFormFieldLimit() {
    SelectedEdition edition =
        new SelectedEdition(
            "googlebooks",
            "google-volume",
            "Children of Blood and Bone",
            Optional.empty(),
            List.of("Tomi Adeyemi"),
            Optional.empty(),
            Optional.of("Macmillan"),
            Optional.empty(),
            Optional.of(531),
            Optional.of("en"),
            Optional.of("x".repeat(1_500)),
            Optional.empty(),
            Optional.of(new Isbn13("9780132350884")),
            Optional.empty());
    String reviewToken = tokens.issue("9780132350884", edition);

    assertTrue(reviewToken.getBytes(StandardCharsets.UTF_8).length > 2_048);
    given()
        .redirects()
        .follow(false)
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .contentType("application/x-www-form-urlencoded")
        .formParam("reviewToken", reviewToken)
        .formParam("intent", "invalid")
        .formParam("state", "TO_READ")
        .post("/books/new/add")
        .then()
        .statusCode(400);
  }

  @Test
  void addsTheReviewedEditionThenRedirectsToTheNextScanner() {
    SelectedEdition edition =
        new SelectedEdition(
            "googlebooks",
            "batch-" + UUID.randomUUID(),
            "Batch scan title",
            Optional.empty(),
            List.of("Author"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(new Isbn13("9781509871353")),
            Optional.empty());
    String reviewToken = tokens.issue("9781509871353", edition);

    given()
        .redirects()
        .follow(false)
        .cookie("rosies-dev-user", DevelopmentUser.READER_ONE.alias())
        .contentType("application/x-www-form-urlencoded")
        .formParam("reviewToken", reviewToken)
        .formParam("intent", "confirm-and-scan-next")
        .formParam("state", "TO_READ")
        .post("/books/new/add")
        .then()
        .statusCode(303)
        .header("Location", "http://localhost:8081/books/new?scan=true");
  }
}
