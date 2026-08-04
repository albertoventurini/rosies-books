package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfCatalog;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
class ShelfResource {

  private final CurrentUserProvider currentUsers;
  private final ShelfCatalog shelves;

  ShelfResource(CurrentUserProvider currentUsers, ShelfCatalog shelves) {
    this.currentUsers = currentUsers;
    this.shelves = shelves;
  }

  @GET
  @Path("reading")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance reading() {
    return render(Shelf.READING);
  }

  @GET
  @Path("to-read")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance toRead() {
    return render(Shelf.TO_READ);
  }

  @GET
  @Path("finished")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance finished() {
    return render(Shelf.FINISHED);
  }

  private TemplateInstance render(Shelf shelf) {
    CurrentUser owner =
        currentUsers
            .currentUser()
            .orElseThrow(
                () ->
                    new WebApplicationException(
                        Response.status(Response.Status.UNAUTHORIZED).build()));
    return ShelfTemplates.shelf(
        ShelfPage.from(owner.displayLabel(), shelf, shelves.find(owner, shelf)));
  }
}
