package com.albertoventurini.rosiesbooks.platform.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(UnreachableDatasourceHealthProfile.class)
class UnreachableDatasourceHealthTest {

  @Test
  void databaseFailureMakesReadinessDownWithoutLeakingPrivateConfiguration() {
    Response response = given().when().get("/q/health/ready");

    response.then().statusCode(503).body("status", is("DOWN"));
    String body = response.asString();
    assertThat(body, not(containsString(UnreachableDatasourceHealthProfile.USERNAME)));
    assertThat(body, not(containsString(UnreachableDatasourceHealthProfile.PASSWORD)));
    assertThat(body, not(containsString("stackTrace")));
    assertThat(body, not(containsString("SQLException")));
    assertThat(body, not(containsString("org.postgresql")));
  }

  @Test
  void databaseFailureDoesNotChangeStartupOrLiveness() {
    given().when().get("/q/health/started").then().statusCode(200).body("status", is("UP"));
    given().when().get("/q/health/live").then().statusCode(200).body("status", is("UP"));
  }
}
