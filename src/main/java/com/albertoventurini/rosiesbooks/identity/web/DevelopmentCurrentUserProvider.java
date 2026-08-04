package com.albertoventurini.rosiesbooks.identity.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.arc.profile.IfBuildProfile;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
class DevelopmentCurrentUserProvider implements CurrentUserProvider {

  static final String COOKIE_NAME = "rosies-dev-user";

  private final RoutingContext request;

  DevelopmentCurrentUserProvider(RoutingContext request) {
    this.request = request;
  }

  @Override
  public Optional<CurrentUser> currentUser() {
    Cookie cookie = request.request().getCookie(COOKIE_NAME);
    if (cookie == null) {
      return Optional.empty();
    }
    return DevelopmentUser.fromAlias(cookie.getValue()).map(DevelopmentUser::currentUser);
  }
}
