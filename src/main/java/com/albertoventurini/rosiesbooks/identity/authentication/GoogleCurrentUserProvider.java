package com.albertoventurini.rosiesbooks.identity.authentication;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.identity.persistence.GoogleUserProvisioner;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.Optional;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
@Alternative
@Priority(1)
@IfBuildProfile(anyOf = {"prod", "local-oidc"})
@IfBuildProperty(name = "rosies-books.oidc.enabled", stringValue = "true")
class GoogleCurrentUserProvider implements CurrentUserProvider {

  private final SecurityIdentity securityIdentity;
  private final GoogleUserProvisioner users;
  private final GoogleIdentityValidator identities;

  GoogleCurrentUserProvider(
      SecurityIdentity securityIdentity, GoogleUserProvisioner users, GoogleOidcConfig config) {
    this.securityIdentity = securityIdentity;
    this.users = users;
    this.identities = new GoogleIdentityValidator(config.allowedEmails());
  }

  @Override
  public Optional<CurrentUser> currentUser() {
    if (!(securityIdentity.getPrincipal() instanceof JsonWebToken token)) {
      return Optional.empty();
    }
    return identities
        .validate(
            token.getIssuer(),
            token.getSubject(),
            token.getClaim("email"),
            token.getClaim("email_verified"))
        .map(identity -> users.resolve(identity.issuer(), identity.subject(), identity.email()));
  }
}
