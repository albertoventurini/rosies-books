package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ReadingStateTransition;
import com.albertoventurini.rosiesbooks.library.internal.ReadingStateTransitions;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import com.albertoventurini.rosiesbooks.library.internal.TransitionPlan;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;

/** Owner-scoped state loading and optimistic state transitions for the web workflow. */
@ApplicationScoped
public class StateChangeService {

  private final DSLContext dsl;
  private final UserEditionRepository userEditions;
  private final ReadingStateTransitions transitions = new ReadingStateTransitions();

  StateChangeService(DSLContext dsl, UserEditionRepository userEditions) {
    this.dsl = dsl;
    this.userEditions = userEditions;
  }

  public Optional<BookState> find(CurrentUser owner, UserEditionId id) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(id, "id");
    return dsl.select(
            USER_EDITION.STATE,
            USER_EDITION.STARTED_ON,
            USER_EDITION.FINISHED_ON,
            USER_EDITION.EFFECTIVE_TITLE_SEARCH,
            USER_EDITION.VERSION)
        .from(USER_EDITION)
        .where(USER_EDITION.USER_ID.eq(owner.id().value()).and(USER_EDITION.ID.eq(id.value())))
        .fetchOptional(
            row ->
                new BookState(
                    id,
                    row.get(USER_EDITION.EFFECTIVE_TITLE_SEARCH),
                    state(
                        row.get(USER_EDITION.STATE),
                        row.get(USER_EDITION.STARTED_ON),
                        row.get(USER_EDITION.FINISHED_ON)),
                    row.get(USER_EDITION.VERSION)));
  }

  public TransitionPlan plan(BookState current, ReadingStateTransition transition) {
    return transitions.plan(current.state(), transition);
  }

  @Transactional
  public ChangeResult change(
      CurrentUser owner,
      UserEditionId id,
      long expectedVersion,
      ReadingStateTransition transition,
      boolean confirmed,
      Instant updatedAt) {
    Optional<BookState> loaded = find(owner, id);
    if (loaded.isEmpty()) {
      return new ChangeResult(ChangeStatus.NOT_FOUND, null, null);
    }
    BookState current = loaded.orElseThrow();
    if (current.version() != expectedVersion) {
      return new ChangeResult(ChangeStatus.CONFLICT, current, null);
    }
    TransitionPlan plan = transitions.plan(current.state(), transition);
    if (plan.confirmationRequirement().isPresent() && !confirmed) {
      return new ChangeResult(ChangeStatus.CONFIRMATION_REQUIRED, current, plan.resultingState());
    }
    if (plan.confirmationRequirement().isEmpty() && confirmed) {
      throw new IllegalArgumentException("Confirmation is not valid for this transition");
    }
    if (!userEditions.updateState(owner, id, expectedVersion, plan.resultingState(), updatedAt)) {
      Optional<BookState> afterFailedUpdate = find(owner, id);
      return afterFailedUpdate
          .map(value -> new ChangeResult(ChangeStatus.CONFLICT, value, null))
          .orElseGet(() -> new ChangeResult(ChangeStatus.NOT_FOUND, null, null));
    }
    return new ChangeResult(ChangeStatus.CHANGED, current, plan.resultingState());
  }

  private static ReadingState state(String state, LocalDate startedOn, LocalDate finishedOn) {
    return switch (state) {
      case "TO_READ" -> new ToRead();
      case "READING" -> new Reading(startedOn);
      case "FINISHED" -> new Finished(Optional.ofNullable(startedOn), finishedOn);
      default -> throw new IllegalArgumentException("Unknown persisted reading state: " + state);
    };
  }

  public record BookState(UserEditionId id, String title, ReadingState state, long version) {}

  public enum ChangeStatus {
    CHANGED,
    CONFIRMATION_REQUIRED,
    CONFLICT,
    NOT_FOUND
  }

  public record ChangeResult(ChangeStatus status, BookState current, ReadingState resultingState) {}
}
