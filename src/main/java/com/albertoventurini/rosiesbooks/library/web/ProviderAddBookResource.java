package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import com.albertoventurini.rosiesbooks.library.persistence.ProviderBookAdditionService;
import com.albertoventurini.rosiesbooks.library.persistence.ProviderBookAdditionService.IdentifierConflictException;
import com.albertoventurini.rosiesbooks.library.persistence.ProviderBookAdditionService.StaleReviewException;
import com.albertoventurini.rosiesbooks.library.persistence.ProviderCoverPersistenceService;
import com.albertoventurini.rosiesbooks.provider.api.IsbnEditionLookup;
import com.albertoventurini.rosiesbooks.provider.api.IsbnLookupResult;
import com.albertoventurini.rosiesbooks.provider.api.SelectedEdition;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;

@Path("/books/new")
class ProviderAddBookResource {
  private final CurrentUserProvider currentUsers;
  private final IsbnEditionLookup lookup;
  private final ProviderReviewToken tokens;
  private final ProviderBookAdditionService additions;
  private final ManualBookEntryValidator stateValidator;
  private final ProviderCoverPersistenceService covers;

  ProviderAddBookResource(
      CurrentUserProvider currentUsers,
      IsbnEditionLookup lookup,
      ProviderReviewToken tokens,
      ProviderBookAdditionService additions,
      ProviderCoverPersistenceService covers,
      Clock clock,
      @ConfigProperty(name = "rosies-books.default-zone", defaultValue = "Africa/Johannesburg")
          String zone) {
    this.currentUsers = currentUsers;
    this.lookup = lookup;
    this.tokens = tokens;
    this.additions = additions;
    this.covers = covers;
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
    var local = additions.findByIsbn13(normalized.value());
    if (local.isPresent()) {
      retryCover(local.get());
      local = additions.findByIsbn13(normalized.value());
      SelectedEdition edition = selected(local.get().metadata());
      return Response.ok(
              ProviderBookTemplates.add(
                  ProviderAddBookPage.found(
                      owner.displayLabel(),
                      submitted,
                      edition,
                      tokens.issueLocal(normalized.value(), local.get().id()),
                      initialStateForm(),
                      local.get().coverHash() == null
                          ? null
                          : "/covers/" + local.get().coverHash())))
          .build();
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
    try {
      var added =
          accepted.get().local()
              ? additions.addLocal(owner, accepted.get().localEditionId(), readingState(form))
              : additions.addProvider(owner, candidate(accepted.get()), readingState(form));
      if (accepted.get().local())
        additions.findByIsbn13(accepted.get().lookupIsbn()).ifPresent(this::retryCover);
      else
        accepted
            .get()
            .edition()
            .cover()
            .ifPresent(source -> attemptCover(added.editionId(), source));
      return Response.seeOther(URI.create("/books/" + added.id().value() + "?notice=book-added"))
          .build();
    } catch (StaleReviewException exception) {
      return bad(
          ProviderAddBookPage.error(
              owner.displayLabel(),
              "",
              "This result is no longer available. Look up the ISBN again."));
    } catch (IdentifierConflictException exception) {
      return badAdd(
          owner,
          accepted.get(),
          reviewToken,
          form.withErrors(
              Map.of(
                  "form",
                  List.of(
                      "This result conflicts with an existing edition. Look up the ISBN again."))));
    } catch (IllegalArgumentException exception) {
      return badAdd(
          owner,
          accepted.get(),
          reviewToken,
          form.withErrors(
              Map.of(
                  "form", List.of("This result is no longer available. Look up the ISBN again."))));
    }
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

  private ProviderAddBookPage addPage(
      CurrentUser owner, AcceptedProviderReview accepted, String token, ManualBookForm form) {
    SelectedEdition edition =
        accepted.local()
            ? additions
                .findByIsbn13(accepted.lookupIsbn())
                .map(value -> selected(value.metadata()))
                .orElse(null)
            : accepted.edition();
    if (edition == null)
      return ProviderAddBookPage.error(
          owner.displayLabel(), "", "This result is no longer available. Look up the ISBN again.");
    String cover =
        accepted.local()
            ? additions
                .findByIsbn13(accepted.lookupIsbn())
                .map(ProviderBookAdditionService.LocalEdition::coverHash)
                .map(hash -> hash == null ? null : "/covers/" + hash)
                .orElse(null)
            : null;
    return ProviderAddBookPage.found(
        owner.displayLabel(), accepted.lookupIsbn(), edition, token, form, cover);
  }

  private static Response bad(ProviderAddBookPage page) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(ProviderBookTemplates.add(page))
        .build();
  }

  private Response badAdd(
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

  private static ProviderBookAdditionService.ProviderCandidate candidate(
      AcceptedProviderReview accepted) {
    SelectedEdition edition = accepted.edition();
    return new ProviderBookAdditionService.ProviderCandidate(
        accepted.lookupIsbn(),
        edition.providerName(),
        edition.providerEditionId(),
        metadata(edition, accepted.lookupIsbn()),
        edition.cover());
  }

  private void attemptCover(
      UUID editionId, com.albertoventurini.rosiesbooks.provider.api.TrustedCoverReference source) {
    try {
      covers.fetchAndAttach(editionId, source);
    } catch (RuntimeException ignored) {
      // The already committed book remains usable if durable-cover persistence is unavailable.
    }
  }

  private void retryCover(ProviderBookAdditionService.LocalEdition edition) {
    try {
      covers.retryIfCoverless(edition);
    } catch (RuntimeException ignored) {
      // ISBN lookup remains available when a retry cannot be persisted.
    }
  }

  private static EditionMetadata metadata(SelectedEdition edition, String lookupIsbn) {
    return new EditionMetadata(
        edition.title(),
        edition.subtitle(),
        edition.authors(),
        edition.format(),
        edition
            .isbn10()
            .map(
                value ->
                    com.albertoventurini.rosiesbooks.library.internal.Isbn10.parse(value.value())),
        java.util.Optional.of(Isbn13.parse(lookupIsbn)),
        edition.publisher(),
        edition
            .publicationDate()
            .map(
                value ->
                    new com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate(
                        value.year(), value.month(), value.day())),
        edition.pageCount(),
        edition.language(),
        edition.description());
  }

  private static SelectedEdition selected(EditionMetadata metadata) {
    return new SelectedEdition(
        "local",
        "local",
        metadata.title(),
        metadata.subtitle(),
        metadata.authors(),
        metadata.format(),
        metadata.publisher(),
        metadata
            .publicationDate()
            .map(
                value ->
                    new com.albertoventurini.rosiesbooks.provider.api.PartialPublicationDate(
                        value.year(), value.month(), value.day())),
        metadata.pageCount(),
        metadata.language(),
        metadata.description(),
        metadata
            .isbn10()
            .map(value -> new com.albertoventurini.rosiesbooks.provider.api.Isbn10(value.value())),
        metadata
            .isbn13()
            .map(value -> new com.albertoventurini.rosiesbooks.provider.api.Isbn13(value.value())),
        java.util.Optional.empty());
  }

  private static ReadingState readingState(ManualBookForm form) {
    return switch (form.state()) {
      case "TO_READ" -> new ToRead();
      case "READING" -> new Reading(LocalDate.parse(form.startedOn()));
      case "FINISHED" ->
          new Finished(
              form.startedOn().isBlank()
                  ? java.util.Optional.empty()
                  : java.util.Optional.of(LocalDate.parse(form.startedOn())),
              LocalDate.parse(form.finishedOn()));
      default -> throw new IllegalArgumentException("Invalid reading state");
    };
  }
}
