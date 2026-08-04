package com.albertoventurini.rosiesbooks.identity.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DevelopmentUserSelectorTest {

  @Test
  void getRendersOnlySafeDevelopmentIdentityValues() {
    String body = given().when().get("/dev/users").then().statusCode(200).extract().asString();

    assertThat(body, containsString("Reader One"));
    assertThat(body, containsString("Reader Two"));
    for (DevelopmentUser user : DevelopmentUser.all()) {
      assertThat(body, not(containsString(user.currentUser().id().value().toString())));
      assertThat(body, not(containsString(user.email())));
      assertThat(body, not(containsString(user.oidcSubject())));
    }
    assertThat(body, not(containsString(DevelopmentUser.OIDC_ISSUER)));
  }

  @Test
  void validPostSetsAnAttributedAliasCookieAndTheNextRequestResolvesIt() {
    Response response =
        given()
            .redirects()
            .follow(false)
            .formParam("alias", DevelopmentUser.READER_ONE.alias())
            .when()
            .post("/dev/users");

    response.then().statusCode(303).header("Location", containsString("/"));
    String setCookie = response.header("Set-Cookie");
    assertThat(setCookie, containsString("rosies-dev-user=reader-one"));
    assertThat(setCookie, containsString("Path=/"));
    assertThat(setCookie, containsString("HttpOnly"));
    assertThat(setCookie, containsString("SameSite=Lax"));
    assertThat(setCookie, not(containsString(userId(DevelopmentUser.READER_ONE))));
    assertThat(setCookie, not(containsString(DevelopmentUser.READER_ONE.email())));

    given()
        .cookie(DevelopmentCurrentUserProvider.COOKIE_NAME, DevelopmentUser.READER_ONE.alias())
        .when()
        .get("/dev/users")
        .then()
        .statusCode(200)
        .body(containsString("Reader One (selected)"));
  }

  @Test
  void postingAnotherAliasSwitchesTheResolvedUser() {
    given()
        .cookie(DevelopmentCurrentUserProvider.COOKIE_NAME, DevelopmentUser.READER_ONE.alias())
        .redirects()
        .follow(false)
        .formParam("alias", DevelopmentUser.READER_TWO.alias())
        .when()
        .post("/dev/users")
        .then()
        .statusCode(303)
        .header("Set-Cookie", containsString("rosies-dev-user=reader-two"));

    given()
        .cookie(DevelopmentCurrentUserProvider.COOKIE_NAME, DevelopmentUser.READER_TWO.alias())
        .when()
        .get("/dev/users")
        .then()
        .statusCode(200)
        .body(containsString("Reader Two (selected)"))
        .body(not(containsString("Reader One (selected)")));
  }

  @Test
  void missingMalformedAndUnknownCookiesResolveNoUser() {
    given().when().get("/dev/users").then().statusCode(200).body(not(containsString("(selected)")));

    for (String value :
        new String[] {"", "reader%20one", "unknown", userId(DevelopmentUser.READER_ONE)}) {
      given()
          .header("Cookie", DevelopmentCurrentUserProvider.COOKIE_NAME + "=" + value)
          .when()
          .get("/dev/users")
          .then()
          .statusCode(200)
          .body(not(containsString("(selected)")));
    }
  }

  @Test
  void invalidPostsReturnBadRequestWithoutChangingTheCookie() {
    given()
        .formParam("alias", " reader-one ")
        .when()
        .post("/dev/users")
        .then()
        .statusCode(400)
        .header("Set-Cookie", nullValue())
        .body(containsString("Choose one of the listed development users."));

    given()
        .contentType("application/x-www-form-urlencoded")
        .when()
        .post("/dev/users")
        .then()
        .statusCode(400)
        .header("Set-Cookie", nullValue());
  }

  private static String userId(DevelopmentUser user) {
    return user.currentUser().id().value().toString();
  }
}
