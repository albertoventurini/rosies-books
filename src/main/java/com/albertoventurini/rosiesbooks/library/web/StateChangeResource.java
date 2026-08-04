package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.internal.MoveToFinished;
import com.albertoventurini.rosiesbooks.library.internal.MoveToRead;
import com.albertoventurini.rosiesbooks.library.internal.MoveToReading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingStateTransition;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import com.albertoventurini.rosiesbooks.library.persistence.StateChangeService;
import com.albertoventurini.rosiesbooks.library.persistence.StateChangeService.BookState;
import com.albertoventurini.rosiesbooks.library.persistence.StateChangeService.ChangeResult;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;

@Path("/books/{id}/state")
class StateChangeResource {

  private final CurrentUserProvider currentUsers;
  private final StateChangeService changes;
  private final Clock clock;
  private final ZoneId zone;

  StateChangeResource(
      CurrentUserProvider currentUsers,
      StateChangeService changes,
      Clock clock,
      @ConfigProperty(name = "rosies-books.default-zone", defaultValue = "Africa/Johannesburg")
          String defaultZone) {
    this.currentUsers = currentUsers;
    this.changes = changes;
    this.clock = clock;
    this.zone = ZoneId.of(defaultZone);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance form(@PathParam("id") String rawId, @QueryParam("target") String target) {
    CurrentUser owner = requireCurrentUser();
    BookState book = find(owner, rawId);
    String selected = validTarget(book, target) ? target : defaultTarget(book);
    String readingStart = today().toString();
    String finish = today().toString();
    return StateChangeTemplates.state(
        StateChangePage.form(
            owner.displayLabel(), book, selected, readingStart, "", finish, Map.of()));
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response submit(
      @PathParam("id") String rawId,
      @RestForm String intent,
      @RestForm String version,
      @RestForm String target,
      @RestForm String readingStartedOn,
      @RestForm String finishedStartedOn,
      @RestForm String finishedOn) {
    CurrentUser owner = requireCurrentUser();
    BookState book = find(owner, rawId);
    Map<String, List<String>> errors = new LinkedHashMap<>();
    Long parsedVersion = parseVersion(version, errors);
    String action = intent == null ? "" : intent;

    if (action.equals("cancel") && parsedVersion != null && validTarget(book, target)) {
      return redirect(book.state(), "state-change-cancelled");
    }
    if (parsedVersion != null && parsedVersion != book.version()) {
      return Response.status(Response.Status.CONFLICT)
          .entity(StateChangeTemplates.state(StateChangePage.conflict(owner.displayLabel(), book)))
          .build();
    }
    if (!action.equals("change") && !action.equals("confirm")) {
      errors.put("form", List.of("Choose one of the available form actions."));
    }
    if (!validTarget(book, target)) {
      errors.put("target", List.of("Choose a valid destination shelf."));
    }

    ReadingStateTransition transition = null;
    if (!errors.containsKey("target")) {
      transition =
          transition(book, target, readingStartedOn, finishedStartedOn, finishedOn, errors);
    }
    if (!errors.isEmpty()) {
      return badRequest(
          owner, book, target, readingStartedOn, finishedStartedOn, finishedOn, errors);
    }

    ChangeResult result;
    try {
      result =
          changes.change(
              owner,
              book.id(),
              parsedVersion,
              transition,
              action.equals("confirm"),
              Instant.now(clock));
    } catch (IllegalArgumentException exception) {
      return badRequest(
          owner,
          book,
          target,
          readingStartedOn,
          finishedStartedOn,
          finishedOn,
          Map.of("finishedOn", List.of(exception.getMessage())));
    }

    return switch (result.status()) {
      case CHANGED -> redirect(result.resultingState(), "state-changed");
      case CONFIRMATION_REQUIRED ->
          Response.ok(
                  StateChangeTemplates.state(
                      StateChangePage.confirmation(owner.displayLabel(), result.current())))
              .build();
      case CONFLICT ->
          Response.status(Response.Status.CONFLICT)
              .entity(
                  StateChangeTemplates.state(
                      StateChangePage.conflict(owner.displayLabel(), result.current())))
              .build();
      case NOT_FOUND -> throw notFound();
    };
  }

  private ReadingStateTransition transition(
      BookState book,
      String target,
      String readingStartedOn,
      String finishedStartedOn,
      String finishedOn,
      Map<String, List<String>> errors) {
    return switch (target) {
      case "TO_READ" -> {
        if (!blank(readingStartedOn) || !blank(finishedStartedOn) || !blank(finishedOn)) {
          errors.put("form", List.of("Dates cannot be supplied when moving to To Read."));
        }
        yield new MoveToRead();
      }
      case "READING" -> {
        if (!blank(finishedOn)) {
          errors.put("finishedOn", List.of("A Reading book cannot have a finish date."));
        }
        if (readingStartIsEditable(book)) {
          LocalDate start =
              parseDate(readingStartedOn, "readingStartedOn", true, errors).orElse(null);
          yield start == null ? null : new MoveToReading(start);
        }
        if (!blank(readingStartedOn)) {
          errors.put("readingStartedOn", List.of("The existing start date cannot be replaced."));
        }
        yield new MoveToReading(today());
      }
      case "FINISHED" -> {
        LocalDate finish = parseDate(finishedOn, "finishedOn", true, errors).orElse(null);
        Optional<LocalDate> start =
            parseDate(finishedStartedOn, "finishedStartedOn", false, errors);
        if (!(book.state() instanceof com.albertoventurini.rosiesbooks.library.internal.ToRead)
            && start.isPresent()) {
          errors.put("finishedStartedOn", List.of("The existing start date cannot be replaced."));
        }
        yield finish == null ? null : new MoveToFinished(finish, start);
      }
      default -> null;
    };
  }

  private Optional<LocalDate> parseDate(
      String raw, String field, boolean defaultToday, Map<String, List<String>> errors) {
    if (blank(raw)) return defaultToday ? Optional.of(today()) : Optional.empty();
    try {
      return Optional.of(LocalDate.parse(raw));
    } catch (DateTimeException exception) {
      errors.put(field, List.of("Enter a valid date."));
      return Optional.empty();
    }
  }

  private static boolean readingStartIsEditable(BookState book) {
    return book.state() instanceof com.albertoventurini.rosiesbooks.library.internal.ToRead
        || book.state()
                instanceof com.albertoventurini.rosiesbooks.library.internal.Finished finished
            && finished.startedOn().isEmpty();
  }

  private BookState find(CurrentUser owner, String rawId) {
    UserEditionId id;
    try {
      UUID value = UUID.fromString(rawId == null ? "" : rawId);
      if (!value.toString().equalsIgnoreCase(rawId)) throw new IllegalArgumentException();
      id = new UserEditionId(value);
    } catch (IllegalArgumentException exception) {
      throw notFound();
    }
    return changes.find(owner, id).orElseThrow(StateChangeResource::notFound);
  }

  private static Long parseVersion(String raw, Map<String, List<String>> errors) {
    try {
      long value = Long.parseLong(raw == null ? "" : raw);
      if (value < 0 || !Long.toString(value).equals(raw)) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException exception) {
      errors.put("form", List.of("This form version is not valid."));
      return null;
    }
  }

  private static boolean validTarget(BookState book, String target) {
    if (target == null) return false;
    String current = persisted(book);
    return switch (current) {
      case "TO_READ" -> target.equals("READING") || target.equals("FINISHED");
      case "READING" -> target.equals("TO_READ") || target.equals("FINISHED");
      case "FINISHED" -> target.equals("TO_READ") || target.equals("READING");
      default -> false;
    };
  }

  private static String defaultTarget(BookState book) {
    return switch (persisted(book)) {
      case "TO_READ" -> "READING";
      case "READING", "FINISHED" -> "TO_READ";
      default -> throw new IllegalStateException();
    };
  }

  private static String persisted(BookState book) {
    if (book.state() instanceof com.albertoventurini.rosiesbooks.library.internal.ToRead)
      return "TO_READ";
    if (book.state() instanceof com.albertoventurini.rosiesbooks.library.internal.Reading)
      return "READING";
    return "FINISHED";
  }

  LocalDate today() {
    return LocalDate.now(clock.withZone(zone));
  }

  private CurrentUser requireCurrentUser() {
    return currentUsers
        .currentUser()
        .orElseThrow(
            () ->
                new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build()));
  }

  private static Response badRequest(
      CurrentUser owner,
      BookState book,
      String target,
      String readingStartedOn,
      String finishedStartedOn,
      String finishedOn,
      Map<String, List<String>> errors) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(
            StateChangeTemplates.state(
                StateChangePage.form(
                    owner.displayLabel(),
                    book,
                    target == null ? "" : target,
                    readingStartedOn,
                    finishedStartedOn,
                    finishedOn,
                    errors)))
        .build();
  }

  private static Response redirect(
      com.albertoventurini.rosiesbooks.library.internal.ReadingState state, String notice) {
    return Response.seeOther(URI.create(StateChangePage.route(state) + "?notice=" + notice))
        .build();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static WebApplicationException notFound() {
    return new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
  }
}
