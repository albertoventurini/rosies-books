package com.albertoventurini.rosiesbooks.identity.internal;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fixed local identities shared by the development seeder and selector adapters. */
public enum DevelopmentUser {
  READER_ONE("00000000-0000-0000-0000-000000000001", "reader-one", "Reader One"),
  READER_TWO("00000000-0000-0000-0000-000000000002", "reader-two", "Reader Two");

  public static final String OIDC_ISSUER = "https://oidc.rosies-books.invalid/development";
  public static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  private final CurrentUser currentUser;
  private final String alias;
  private final String displayLabel;

  DevelopmentUser(String id, String alias, String displayLabel) {
    this.currentUser = new CurrentUser(new UserId(UUID.fromString(id)));
    this.alias = alias;
    this.displayLabel = displayLabel;
  }

  public CurrentUser currentUser() {
    return currentUser;
  }

  public String alias() {
    return alias;
  }

  public String displayLabel() {
    return displayLabel;
  }

  public String oidcSubject() {
    return "development:" + alias;
  }

  public String email() {
    return alias + "@rosies-books.invalid";
  }

  public static List<DevelopmentUser> all() {
    return List.of(values());
  }

  public static Optional<DevelopmentUser> fromAlias(String alias) {
    if (alias == null) {
      return Optional.empty();
    }
    return all().stream().filter(user -> user.alias.equals(alias)).findFirst();
  }
}
