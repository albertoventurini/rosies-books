package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.provider.api.IsbnEditionLookup;
import com.albertoventurini.rosiesbooks.provider.api.IsbnLookupResult;
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

@Path("/books/new")
class ProviderAddBookResource {
  private final CurrentUserProvider currentUsers;
  private final IsbnEditionLookup lookup;
  private final ProviderReviewToken tokens;
  private final ManualBookEntryValidator stateValidator;

  ProviderAddBookResource(
      CurrentUserProvider currentUsers,
      IsbnEditionLookup lookup,
      ProviderReviewToken tokens,
      Clock clock,
      @ConfigProperty(name = "rosies-books.default-zone", defaultValue = "Africa/Johannesburg")
          String zone) {
    this.currentUsers = currentUsers;
    this.lookup = lookup;
    this.tokens = tokens;
    this.stateValidator = new ManualBookEntryValidator(clock, ZoneId.of(zone));
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance landing() {
    return ProviderBookTemplates.add(ProviderAddBookPage.empty(owner().displayLabel()));
  }

  @POST
  @Path("lookup")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response lookup(@RestForm String isbn) {
    CurrentUser owner = owner();
    String submitted = isbn == null ? "" : isbn;
    com.albertoventurini.rosiesbooks.provider.api.Isbn13 normalized;
    try {
      normalized = normalize(submitted);
    } catch (IllegalArgumentException invalid) {
      return bad(
          ProviderAddBookPage.error(
              owner.displayLabel(), submitted, "Enter a valid ISBN-10 or ISBN-13."));
    }
    IsbnLookupResult result = lookup.lookup(normalized);
    if (result instanceof IsbnLookupResult.Found found)
      return Response.ok(
              ProviderBookTemplates.add(
                  ProviderAddBookPage.found(
                      owner.displayLabel(),
                      submitted,
                      found.edition(),
                      tokens.issue(normalized.value(), found.edition()),
                      initialStateForm())))
          .build();
    return Response.ok(
            ProviderBookTemplates.add(
                ProviderAddBookPage.error(owner.displayLabel(), submitted, message(result))))
        .build();
  }

  @POST
  @Path("add")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response add(
      @RestForm String reviewToken,
      @RestForm String intent,
      @RestForm String state,
      @RestForm String startedOn,
      @RestForm String finishedOn) {
    CurrentUser owner = owner();
    var accepted = tokens.accept(reviewToken);
    if (accepted.isEmpty())
      return bad(
          ProviderAddBookPage.error(
              owner.displayLabel(),
              "",
              "This result is no longer available. Look up the ISBN again."));
    ManualBookForm form =
        stateValidator.prepare(stateForm(state == null ? "TO_READ" : state, startedOn, finishedOn));
    if ("change-state".equals(intent))
      return Response.ok(
              ProviderBookTemplates.add(addPage(owner, accepted.get(), reviewToken, form)))
          .build();
    if (!"confirm".equals(intent))
      return badAdd(
          owner,
          accepted.get(),
          reviewToken,
          form.withErrors(Map.of("form", List.of("Choose one of the available form actions."))));
    form = stateValidator.validateState(form);
    if (!form.errors().isEmpty()) return badAdd(owner, accepted.get(), reviewToken, form);
    // Persistence is intentionally deferred to task 7-3; this route only establishes the contract.
    return Response.status(Response.Status.NOT_IMPLEMENTED)
        .entity(ProviderBookTemplates.add(addPage(owner, accepted.get(), reviewToken, form)))
        .build();
  }

  private ManualBookForm initialStateForm() {
    return stateValidator.prepare(stateForm("TO_READ", null, null));
  }

  private static ManualBookForm stateForm(String state, String startedOn, String finishedOn) {
    return new ManualBookForm(
        "",
        "",
        List.of(""),
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        state,
        startedOn,
        finishedOn,
        Map.of());
  }

  private static ProviderAddBookPage addPage(
      CurrentUser owner, AcceptedProviderReview accepted, String token, ManualBookForm form) {
    return ProviderAddBookPage.found(
        owner.displayLabel(), accepted.lookupIsbn(), accepted.edition(), token, form);
  }

  private static Response bad(ProviderAddBookPage page) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(ProviderBookTemplates.add(page))
        .build();
  }

  private static Response badAdd(
      CurrentUser owner, AcceptedProviderReview accepted, String token, ManualBookForm form) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(ProviderBookTemplates.add(addPage(owner, accepted, token, form)))
        .build();
  }

  private CurrentUser owner() {
    return currentUsers
        .currentUser()
        .orElseThrow(
            () ->
                new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build()));
  }

  private static com.albertoventurini.rosiesbooks.provider.api.Isbn13 normalize(String submitted) {
    if (submitted == null || submitted.length() > 64) throw new IllegalArgumentException();
    try {
      return new com.albertoventurini.rosiesbooks.provider.api.Isbn13(
          Isbn10.parse(submitted.strip()).toIsbn13().value());
    } catch (IllegalArgumentException notTen) {
      return new com.albertoventurini.rosiesbooks.provider.api.Isbn13(
          Isbn13.parse(submitted.strip()).value());
    }
  }

  private static String message(IsbnLookupResult result) {
    if (result instanceof IsbnLookupResult.NotFound) return "ISBN not found.";
    if (result instanceof IsbnLookupResult.RateLimited)
      return "ISBN lookup is temporarily limited.";
    if (result instanceof IsbnLookupResult.MalformedResponse)
      return "ISBN lookup is temporarily unavailable.";
    return "ISBN lookup is temporarily unavailable.";
  }
}
