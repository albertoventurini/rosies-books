package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;

@Path("/books/new/manual")
class ManualBookResource {

  private final CurrentUserProvider currentUsers;
  private final ManualBookEntryValidator validator;

  ManualBookResource(
      CurrentUserProvider currentUsers,
      Clock clock,
      @ConfigProperty(name = "rosies-books.default-zone", defaultValue = "Africa/Johannesburg")
          String defaultZone) {
    this.currentUsers = currentUsers;
    this.validator = new ManualBookEntryValidator(clock, ZoneId.of(defaultZone));
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance form() {
    CurrentUser owner = requireCurrentUser();
    return ManualBookTemplates.manual(
        new ManualBookPage(owner.displayLabel(), ManualBookForm.empty()));
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response submit(
      @RestForm String intent,
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

    String requestedIntent = intent == null ? "" : intent;
    if (requestedIntent.equals("add-author")) {
      return ok(owner, validator.prepare(submitted).addAuthor());
    }
    if (requestedIntent.startsWith("remove-author-")) {
      return ok(owner, validator.prepare(submitted).removeAuthor(authorIndex(requestedIntent)));
    }
    if (requestedIntent.equals("change-state")) {
      return ok(owner, validator.prepare(submitted));
    }
    if (requestedIntent.equals("edit")) {
      return ok(owner, submitted);
    }
    if (!requestedIntent.equals("review")) {
      return badRequest(
          owner,
          submitted.withErrors(
              Map.of("form", List.of("Choose one of the available form actions."))));
    }

    ManualBookValidation validation = validator.validate(submitted);
    if (!validation.valid()) {
      return badRequest(owner, validation.form());
    }
    ManualBookDraft draft = validation.draft().orElseThrow();
    return Response.ok(
            ManualBookTemplates.manualReview(
                new ManualBookReviewPage(
                    owner.displayLabel(), validation.form(), ManualBookReview.from(draft))))
        .build();
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
}
