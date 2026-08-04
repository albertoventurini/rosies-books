package com.albertoventurini.rosiesbooks.identity.authentication;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
@IfBuildProfile("prod")
class UnauthenticatedCurrentUserProvider implements CurrentUserProvider {

  @Override
  public Optional<CurrentUser> currentUser() {
    return Optional.empty();
  }
}
