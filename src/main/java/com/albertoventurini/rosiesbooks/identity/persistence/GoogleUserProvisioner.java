package com.albertoventurini.rosiesbooks.identity.persistence;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Creates or resolves the durable application user for an already validated OIDC identity. */
@ApplicationScoped
public class GoogleUserProvisioner {

  private final UserRepository users;
  private final Clock clock;

  GoogleUserProvisioner(UserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public CurrentUser resolve(String issuer, String subject, String email) {
    User user = users.findByOidcIdentity(issuer, subject).orElse(null);
    Instant now = clock.instant();
    if (user == null) {
      User candidate = new User(new UserId(UUID.randomUUID()), issuer, subject, email, now, now);
      if (users.createIfAbsent(candidate)) {
        user = candidate;
      } else {
        user = users.findByOidcIdentity(issuer, subject).orElseThrow();
      }
    }
    if (!user.email().equals(email)) {
      users.updateEmail(user.id(), email, now);
    }
    return new CurrentUser(user.id(), email);
  }
}
