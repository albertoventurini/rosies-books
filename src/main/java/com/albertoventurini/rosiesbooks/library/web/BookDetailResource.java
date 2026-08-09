package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.api.BookDetailCatalog;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import com.albertoventurini.rosiesbooks.library.persistence.ProviderCoverPersistenceService;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/books/{id}")
class BookDetailResource {

  private final CurrentUserProvider currentUsers;
  private final BookDetailCatalog details;
  private final ProviderCoverPersistenceService covers;

  BookDetailResource(
      CurrentUserProvider currentUsers,
      BookDetailCatalog details,
      ProviderCoverPersistenceService covers) {
    this.currentUsers = currentUsers;
    this.details = details;
    this.covers = covers;
  }

  @POST
  @Path("cover/refresh")
  public Response refreshCover(@PathParam("id") String rawId) {
    CurrentUser owner = requireCurrentUser();
    UserEditionId id = parse(rawId);
    try {
      covers.retryFailed(owner, id);
    } catch (RuntimeException ignored) {
      // Refresh is best effort; the book remains usable with its placeholder.
    }
    return Response.seeOther(java.net.URI.create("/books/" + id.value())).build();
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance detail(
      @PathParam("id") String rawId, @QueryParam("notice") String notice) {
    CurrentUser owner = requireCurrentUser();
    UserEditionId id = parse(rawId);
    return BookDetailTemplates.detail(
        BookDetailPage.from(
            id, details.find(owner, id).orElseThrow(BookDetailResource::notFound), notice));
  }

  private CurrentUser requireCurrentUser() {
    return currentUsers
        .currentUser()
        .orElseThrow(
            () ->
                new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build()));
  }

  private static UserEditionId parse(String rawId) {
    try {
      UUID value = UUID.fromString(rawId == null ? "" : rawId);
      if (!value.toString().equalsIgnoreCase(rawId)) throw new IllegalArgumentException();
      return new UserEditionId(value);
    } catch (IllegalArgumentException exception) {
      throw notFound();
    }
  }

  private static WebApplicationException notFound() {
    return new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
  }
}
