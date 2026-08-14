package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfCatalog;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfSearch;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/")
class ShelfResource {

  private final CurrentUserProvider currentUsers;
  private final ShelfCatalog shelves;
  private final Clock clock;
  private final ZoneId zone;

  ShelfResource(
      CurrentUserProvider currentUsers,
      ShelfCatalog shelves,
      Clock clock,
      @ConfigProperty(name = "rosies-books.default-zone", defaultValue = "Africa/Johannesburg")
          String defaultZone) {
    this.currentUsers = currentUsers;
    this.shelves = shelves;
    this.clock = clock;
    this.zone = ZoneId.of(defaultZone);
  }

  @GET
  @Path("reading")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance reading(@QueryParam("notice") String notice) {
    return render(Shelf.READING, notice);
  }

  @GET
  @Path("to-read")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance toRead(@QueryParam("notice") String notice) {
    return render(Shelf.TO_READ, notice);
  }

  @GET
  @Path("finished")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance finished(
      @QueryParam("year") String requestedYear, @QueryParam("notice") String notice) {
    CurrentUser owner = requireCurrentUser();
    Year currentYear = currentYear();
    Year selectedYear = parseYear(requestedYear, currentYear);
    var finished =
        shelves
            .findFinished(owner, selectedYear, currentYear)
            .orElseThrow(ShelfResource::badRequest);
    return ShelfTemplates.shelf(
        ShelfPage.finished(
            owner.displayLabel(),
            finished,
            notice(notice),
            LocalDate.now(clock.withZone(zone)),
            zone));
  }

  @GET
  @Path("search")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance search(@QueryParam("q") String submitted) {
    CurrentUser owner = requireCurrentUser();
    if (submitted == null) {
      return ShelfTemplates.search(SearchPage.blank());
    }
    return ShelfSearch.parse(submitted)
        .map(
            query ->
                ShelfTemplates.search(
                    SearchPage.results(
                        query.input(),
                        shelves.search(owner, query),
                        LocalDate.now(clock.withZone(zone)),
                        zone)))
        .orElseGet(() -> ShelfTemplates.search(SearchPage.invalid(submitted)));
  }

  private TemplateInstance render(Shelf shelf, String noticeCode) {
    CurrentUser owner = requireCurrentUser();
    return ShelfTemplates.shelf(
        ShelfPage.from(
            owner.displayLabel(),
            shelf,
            shelves.find(owner, shelf),
            notice(noticeCode),
            LocalDate.now(clock.withZone(zone)),
            zone));
  }

  Year currentYear() {
    return Year.now(clock.withZone(zone));
  }

  private CurrentUser requireCurrentUser() {
    return currentUsers
        .currentUser()
        .orElseThrow(
            () ->
                new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build()));
  }

  private static Year parseYear(String requestedYear, Year defaultYear) {
    if (requestedYear == null) {
      return defaultYear;
    }
    try {
      return Year.parse(requestedYear);
    } catch (DateTimeParseException exception) {
      throw badRequest();
    }
  }

  private static WebApplicationException badRequest() {
    return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
  }

  private static String notice(String noticeCode) {
    return switch (noticeCode == null ? "" : noticeCode) {
      case "book-added" -> "The book was added successfully.";
      case "state-changed" -> "The book was moved successfully.";
      case "state-change-cancelled" -> "The shelf change was cancelled.";
      case "book-deleted" -> "The book was deleted permanently.";
      case "book-deletion-cancelled" -> "The book deletion was cancelled.";
      default -> null;
    };
  }
}
