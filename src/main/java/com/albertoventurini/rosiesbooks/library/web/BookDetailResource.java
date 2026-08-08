package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.api.BookDetailCatalog;
import com.albertoventurini.rosiesbooks.library.api.BookDetailCatalog.StoredCover;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/books/{id}")
class BookDetailResource {

  private final CurrentUserProvider currentUsers;
  private final BookDetailCatalog details;

  BookDetailResource(CurrentUserProvider currentUsers, BookDetailCatalog details) {
    this.currentUsers = currentUsers;
    this.details = details;
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance detail(@PathParam("id") String rawId, @QueryParam("notice") String notice) {
    CurrentUser owner = requireCurrentUser();
    UserEditionId id = parse(rawId);
    return BookDetailTemplates.detail(
        BookDetailPage.from(id, details.find(owner, id).orElseThrow(BookDetailResource::notFound), notice));
  }

  @GET
  @Path("cover")
  public Response cover(@PathParam("id") String rawId) {
    CurrentUser owner = requireCurrentUser();
    StoredCover cover =
        details.findCover(owner, parse(rawId)).orElseThrow(BookDetailResource::notFound);
    CacheControl cacheControl = new CacheControl();
    cacheControl.setNoStore(true);
    return Response.ok(cover.content(), cover.mimeType()).cacheControl(cacheControl).build();
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
