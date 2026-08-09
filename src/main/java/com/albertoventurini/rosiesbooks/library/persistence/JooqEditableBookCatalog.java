package com.albertoventurini.rosiesbooks.library.persistence;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.api.EditableBookCatalog;
import com.albertoventurini.rosiesbooks.library.api.EditableBookCatalog.EditableBook;
import com.albertoventurini.rosiesbooks.library.internal.EffectiveMetadataResolver;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverrides;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/** Reuses the detail projection while retaining the private override choices for editing. */
@ApplicationScoped
public class JooqEditableBookCatalog implements EditableBookCatalog {
  private final UserEditionRepository userEditions;
  private final EditionRepository editions;
  private final MetadataOverrideRepository overrides;

  JooqEditableBookCatalog(
      UserEditionRepository userEditions,
      EditionRepository editions,
      MetadataOverrideRepository overrides) {
    this.userEditions = userEditions;
    this.editions = editions;
    this.overrides = overrides;
  }

  @Override
  public Optional<EditableBook> find(CurrentUser owner, UserEditionId id) {
    return userEditions
        .find(owner, id)
        .flatMap(
            book ->
                editions
                    .find(book.editionId())
                    .map(
                        edition -> {
                          MetadataOverrides selected =
                              overrides.find(owner, id).orElse(MetadataOverrides.none());
                          return new EditableBook(
                              edition.metadata(),
                              EffectiveMetadataResolver.resolve(edition.metadata(), selected),
                              selected,
                              book.privateNotes());
                        }));
  }
}
