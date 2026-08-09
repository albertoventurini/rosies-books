package com.albertoventurini.rosiesbooks.identity.authentication;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;

@Startup
@ApplicationScoped
@IfBuildProfile(anyOf = {"prod", "local-oidc"})
class GoogleOidcStartupCheck {

  GoogleOidcStartupCheck(GoogleOidcConfig config) {
    new GoogleIdentityValidator(config.allowedEmails());
  }
}
