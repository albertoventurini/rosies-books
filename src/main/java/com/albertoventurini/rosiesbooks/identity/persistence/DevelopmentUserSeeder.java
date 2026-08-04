package com.albertoventurini.rosiesbooks.identity.persistence;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
class DevelopmentUserSeeder {

  private final UserRepository users;

  DevelopmentUserSeeder(UserRepository users) {
    this.users = users;
  }

  @Startup
  @Transactional
  void seed() {
    for (DevelopmentUser developmentUser : DevelopmentUser.all()) {
      User expected = persistedUser(developmentUser);
      users
          .find(expected.id())
          .ifPresentOrElse(
              existing -> ensureMatching(existing, expected), () -> users.create(expected));
    }
  }

  private static User persistedUser(DevelopmentUser developmentUser) {
    return new User(
        developmentUser.currentUser().id(),
        DevelopmentUser.OIDC_ISSUER,
        developmentUser.oidcSubject(),
        developmentUser.email(),
        DevelopmentUser.CREATED_AT,
        DevelopmentUser.CREATED_AT);
  }

  private static void ensureMatching(User existing, User expected) {
    if (!existing.equals(expected)) {
      throw new IllegalStateException(
          "Conflicting data exists for development user " + expected.id().value());
    }
  }
}
