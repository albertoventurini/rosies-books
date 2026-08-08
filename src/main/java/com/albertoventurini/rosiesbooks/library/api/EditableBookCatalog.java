package com.albertoventurini.rosiesbooks.library.api;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverrides;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import java.util.Optional;

/** Owner-scoped data required to edit one user's private book details. */
public interface EditableBookCatalog {
  Optional<EditableBook> find(CurrentUser owner, UserEditionId id);

  record EditableBook(
      EditionMetadata canonicalMetadata,
      EditionMetadata effectiveMetadata,
      MetadataOverrides overrides,
      String privateNotes) {}
}
