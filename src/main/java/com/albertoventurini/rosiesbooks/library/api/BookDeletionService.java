package com.albertoventurini.rosiesbooks.library.api;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Owner-scoped use case for reviewing and permanently deleting a library book. */
public interface BookDeletionService {

  Optional<DeletionBook> find(CurrentUser owner, UUID userEditionId);

  DeletionResult delete(CurrentUser owner, UUID userEditionId, long expectedVersion);

  enum DeletionShelf {
    TO_READ("To Read", "/to-read"),
    READING("Reading", "/reading"),
    FINISHED("Finished", "/finished");

    private final String label;
    private final String route;

    DeletionShelf(String label, String route) {
      this.label = label;
      this.route = route;
    }

    public String label() {
      return label;
    }

    public String route() {
      return route;
    }
  }

  record DeletionBook(UUID id, String title, DeletionShelf shelf, long version) {
    public DeletionBook {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(shelf, "shelf");
    }
  }

  enum DeletionStatus {
    DELETED,
    CONFLICT,
    NOT_FOUND
  }

  record DeletionResult(DeletionStatus status, DeletionBook current) {
    public DeletionResult {
      Objects.requireNonNull(status, "status");
      if (status != DeletionStatus.NOT_FOUND) Objects.requireNonNull(current, "current");
    }
  }
}
