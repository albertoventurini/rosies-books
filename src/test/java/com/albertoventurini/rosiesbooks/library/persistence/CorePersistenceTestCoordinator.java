package com.albertoventurini.rosiesbooks.library.persistence;

import com.albertoventurini.rosiesbooks.identity.api.UserId;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverrides;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
class CorePersistenceTestCoordinator {

  private final EditionRepository editions;
  private final UserEditionRepository userEditions;
  private final MetadataOverrideRepository overrides;
  private final MetadataOverrideService metadata;

  CorePersistenceTestCoordinator(
      EditionRepository editions,
      UserEditionRepository userEditions,
      MetadataOverrideRepository overrides,
      MetadataOverrideService metadata) {
    this.editions = editions;
    this.userEditions = userEditions;
    this.overrides = overrides;
    this.metadata = metadata;
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
    return metadata.save(owner, id, metadataOverrides);
  }

  @Transactional
  boolean saveOverridesDirect(UserId owner, UserEditionId id, MetadataOverrides metadataOverrides) {
    return overrides.save(owner, id, metadataOverrides);
  }

  @Transactional
  void saveOverridesThenFail(UserId owner, UserEditionId id, MetadataOverrides metadataOverrides) {
    metadata.save(owner, id, metadataOverrides);
    throw new DeliberateFailure();
  }

  static final class DeliberateFailure extends RuntimeException {}
}
