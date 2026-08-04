package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.api.BookDeletionService;
import com.albertoventurini.rosiesbooks.library.api.BookDeletionService.DeletionBook;
import com.albertoventurini.rosiesbooks.library.api.BookDeletionService.DeletionResult;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;

@Path("/books/{id}/delete")
class BookDeletionResource {

  private final CurrentUserProvider currentUsers;
  private final BookDeletionService deletions;

  BookDeletionResource(CurrentUserProvider currentUsers, BookDeletionService deletions) {
    this.currentUsers = currentUsers;
    this.deletions = deletions;
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance confirmation(@PathParam("id") String rawId) {
    CurrentUser owner = requireCurrentUser();
    return BookDeletionTemplates.delete(
        BookDeletionPage.confirmation(owner.displayLabel(), find(owner, rawId)));
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response submit(
      @PathParam("id") String rawId, @RestForm String intent, @RestForm String version) {
    CurrentUser owner = requireCurrentUser();
    DeletionBook book = find(owner, rawId);
    Long expectedVersion = parseVersion(version);
    if (expectedVersion == null) {
      return badRequest(owner, book, "This form version is not valid.");
    }
    if ("cancel".equals(intent)) {
      return redirect(book, "book-deletion-cancelled");
    }
    if (!"delete".equals(intent)) {
      return badRequest(owner, book, "Choose one of the available form actions.");
    }

    DeletionResult result = deletions.delete(owner, book.id(), expectedVersion);
    return switch (result.status()) {
      case DELETED -> redirect(result.current(), "book-deleted");
      case CONFLICT ->
          Response.status(Response.Status.CONFLICT)
              .entity(
                  BookDeletionTemplates.delete(
                      BookDeletionPage.conflict(owner.displayLabel(), result.current())))
              .build();
      case NOT_FOUND -> throw notFound();
    };
  }

  private DeletionBook find(CurrentUser owner, String rawId) {
    UUID id;
    try {
      id = UUID.fromString(rawId == null ? "" : rawId);
      if (!id.toString().equalsIgnoreCase(rawId)) throw new IllegalArgumentException();
    } catch (IllegalArgumentException exception) {
      throw notFound();
    }
    return deletions.find(owner, id).orElseThrow(BookDeletionResource::notFound);
  }

  private CurrentUser requireCurrentUser() {
    return currentUsers
        .currentUser()
        .orElseThrow(
            () ->
                new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build()));
  }

  private static Long parseVersion(String raw) {
    try {
      long parsed = Long.parseLong(raw == null ? "" : raw);
      if (parsed < 0 || !Long.toString(parsed).equals(raw)) throw new NumberFormatException();
      return parsed;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static Response badRequest(CurrentUser owner, DeletionBook book, String error) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(
            BookDeletionTemplates.delete(
                BookDeletionPage.invalid(owner.displayLabel(), book, error)))
        .build();
  }

  private static Response redirect(DeletionBook book, String notice) {
    return Response.seeOther(URI.create(book.shelf().route() + "?notice=" + notice)).build();
  }

  private static WebApplicationException notFound() {
    return new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
  }
}
