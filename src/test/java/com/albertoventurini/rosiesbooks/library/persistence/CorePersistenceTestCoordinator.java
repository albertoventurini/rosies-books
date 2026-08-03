package com.albertoventurini.rosiesbooks.library.persistence;

import com.albertoventurini.rosiesbooks.identity.api.UserId;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
class CorePersistenceTestCoordinator {

  private final EditionRepository editions;
  private final UserEditionRepository userEditions;
  private final MetadataOverrideRepository overrides;

  CorePersistenceTestCoordinator(
      EditionRepository editions,
      UserEditionRepository userEditions,
      MetadataOverrideRepository overrides) {
    this.editions = editions;
    this.userEditions = userEditions;
    this.overrides = overrides;
  }

  @Transactional
  void createEdition(Edition edition) {
    editions.create(edition);
  }

  @Transactional
  void createThenFail(Edition edition) {
    editions.create(edition);
    throw new DeliberateFailure();
  }

  @Transactional
  void link(UserId owner, UserEdition userEdition) {
    userEditions.link(owner, userEdition);
  }

  @Transactional
  boolean saveOverrides(UserId owner, UserEditionId id, MetadataOverrides metadataOverrides) {
    return overrides.save(owner, id, metadataOverrides);
  }

  static final class DeliberateFailure extends RuntimeException {}
}
