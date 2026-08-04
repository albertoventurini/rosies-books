package com.albertoventurini.rosiesbooks.library.persistence;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.EffectiveMetadataResolver;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverrides;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Optional;

@ApplicationScoped
class MetadataOverrideService {

  private final EditionRepository editions;
  private final UserEditionRepository userEditions;
  private final MetadataOverrideRepository overrides;

  MetadataOverrideService(
      EditionRepository editions,
      UserEditionRepository userEditions,
      MetadataOverrideRepository overrides) {
    this.editions = editions;
    this.userEditions = userEditions;
    this.overrides = overrides;
  }

  @Transactional
  boolean save(CurrentUser owner, UserEditionId id, MetadataOverrides proposed) {
    Optional<Edition> canonical = userEditions.findEditionId(owner, id).flatMap(editions::find);
    if (canonical.isEmpty()) {
      return false;
    }

    EditionMetadata effective =
        EffectiveMetadataResolver.resolve(canonical.orElseThrow().metadata(), proposed);
    if (!overrides.save(owner, id, proposed)) {
      throw new IllegalStateException("User edition disappeared during metadata replacement");
    }
    if (!userEditions.updateSearchProjections(
        owner, id, effective.title(), String.join(" ", effective.authors()))) {
      throw new IllegalStateException("Search projection update failed");
    }
    return true;
  }
}
