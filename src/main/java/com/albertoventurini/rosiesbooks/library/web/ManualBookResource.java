package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.persistence.ManualBookAdditionService;
import com.albertoventurini.rosiesbooks.library.persistence.ManualBookAdditionService.AddedBook;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;

@Path("/books/new/manual")
class ManualBookResource {

  private final CurrentUserProvider currentUsers;
  private final ManualBookAdditionService additions;
  private final ManualBookEntryValidator validator;

  ManualBookResource(
      CurrentUserProvider currentUsers,
      ManualBookAdditionService additions,
      Clock clock,
      @ConfigProperty(name = "rosies-books.default-zone", defaultValue = "Africa/Johannesburg")
          String defaultZone) {
    this.currentUsers = currentUsers;
    this.additions = additions;
    this.validator = new ManualBookEntryValidator(clock, ZoneId.of(defaultZone));
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance form() {
    CurrentUser owner = requireCurrentUser();
    return ManualBookTemplates.manual(
        new ManualBookPage(owner.displayLabel(), ManualBookForm.empty(UUID.randomUUID())));
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response submit(
      @RestForm String intent,
      @RestForm String requestId,
      @RestForm String title,
      @RestForm List<String> authors,
      @RestForm String subtitle,
      @RestForm String format,
      @RestForm String isbn10,
      @RestForm String isbn13,
      @RestForm String publisher,
      @RestForm String publicationDate,
      @RestForm String pageCount,
      @RestForm String language,
      @RestForm String description,
      @RestForm String state,
      @RestForm String startedOn,
      @RestForm String finishedOn) {
    CurrentUser owner = requireCurrentUser();
    ManualBookForm submitted =
        new ManualBookForm(
            requestId,
            title,
            authors,
            subtitle,
            format,
            isbn10,
            isbn13,
            publisher,
            publicationDate,
            pageCount,
            language,
            description,
            state,
            startedOn,
            finishedOn,
            Map.of());

    UUID parsedRequestId;
    try {
      parsedRequestId = UUID.fromString(submitted.requestId());
      if (!parsedRequestId.toString().equalsIgnoreCase(submitted.requestId())) {
        throw new IllegalArgumentException("Non-canonical UUID");
      }
    } catch (IllegalArgumentException exception) {
      return badRequest(
          owner, submitted.withErrors(Map.of("form", List.of("This form request is not valid."))));
    }

    String requestedIntent = intent == null ? "" : intent;
    if (requestedIntent.equals("add-author")) {
      return ok(owner, validator.prepare(submitted).addAuthor());
    }
    if (requestedIntent.startsWith("remove-author-")) {
      int index = authorIndex(requestedIntent);
      if (index >= 0 && index < submitted.authors().size()) {
        return ok(owner, validator.prepare(submitted).removeAuthor(index));
      }
      return unsupportedIntent(owner, submitted);
    }
    if (requestedIntent.equals("change-state")) {
      return ok(owner, validator.prepare(submitted));
    }
    if (!requestedIntent.equals("save")) {
      return unsupportedIntent(owner, submitted);
    }

    ManualBookValidation validation = validator.validate(submitted);
    if (!validation.valid()) {
      return badRequest(owner, validation.form());
    }
    ManualBookDraft draft = validation.draft().orElseThrow();
    AddedBook added = additions.add(owner, parsedRequestId, draft.metadata(), draft.readingState());
    return Response.seeOther(URI.create(routeFor(added.state()))).build();
  }

  private CurrentUser requireCurrentUser() {
    return currentUsers
        .currentUser()
        .orElseThrow(
            () ->
                new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build()));
  }

  private static Response ok(CurrentUser owner, ManualBookForm form) {
    return Response.ok(ManualBookTemplates.manual(new ManualBookPage(owner.displayLabel(), form)))
        .build();
  }

  private static Response badRequest(CurrentUser owner, ManualBookForm form) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(ManualBookTemplates.manual(new ManualBookPage(owner.displayLabel(), form)))
        .build();
  }

  private static int authorIndex(String intent) {
    try {
      return Integer.parseInt(intent.substring("remove-author-".length()));
    } catch (NumberFormatException exception) {
      return -1;
    }
  }

  private static Response unsupportedIntent(CurrentUser owner, ManualBookForm submitted) {
    return badRequest(
        owner,
        submitted.withErrors(Map.of("form", List.of("Choose one of the available form actions."))));
  }

  private static String routeFor(String state) {
    return switch (state) {
      case "TO_READ" -> "/to-read";
      case "READING" -> "/reading";
      case "FINISHED" -> "/finished";
      default -> throw new IllegalArgumentException("Unknown persisted reading state: " + state);
    };
  }
}
