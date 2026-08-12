package com.albertoventurini.rosiesbooks.identity;

import static com.albertoventurini.rosiesbooks.identity.persistence.jooq.Tables.APP_USER;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ProductionIdentityProfile.class)
class ProductionIdentityTest {

  @Inject CurrentUserProvider currentUsers;
  @Inject DSLContext dsl;

  @ConfigProperty(name = "quarkus.oidc.authentication.force-redirect-https-scheme")
  boolean forceRedirectHttpsScheme;

  @ConfigProperty(name = "quarkus.oidc.authentication.restore-path-after-redirect")
  boolean restorePathAfterRedirect;

  @Test
  void productionForcesHttpsForOidcRedirectUris() {
    assertTrue(forceRedirectHttpsScheme);
  }

  @Test
  void productionRestoresTheOriginalPathAfterOidcRedirects() {
    assertTrue(restorePathAfterRedirect);
  }

  @Test
  void productionStartsWithoutSeedsAndFailsClosed() {
    assertTrue(currentUsers.currentUser().isEmpty());
    assertTrue(dsl.selectFrom(APP_USER).fetch().isEmpty());
    given().when().get("/dev/users").then().statusCode(404);
    given().formParam("alias", "reader-one").when().post("/dev/users").then().statusCode(404);
  }
}
