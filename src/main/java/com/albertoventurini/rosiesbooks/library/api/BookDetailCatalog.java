package com.albertoventurini.rosiesbooks.library.api;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import java.util.Optional;

/** Owner-scoped projections used to render a book and deliver its cover. */
public interface BookDetailCatalog {

  Optional<BookDetail> find(CurrentUser owner, UserEditionId id);

  Optional<StoredCover> findCover(CurrentUser owner, UserEditionId id);

  record BookDetail(
      EditionMetadata metadata,
      ReadingState state,
      String privateNotes,
      Shelf shelf,
      String coverHash,
      boolean coverFetchFailed) {}

  record StoredCover(byte[] content, String mimeType) {
    public StoredCover {
      content = content.clone();
    }

    @Override
    public byte[] content() {
      return content.clone();
    }
  }
}
