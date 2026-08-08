package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.api.EditableBookCatalog;
import com.albertoventurini.rosiesbooks.library.api.EditableBookCatalog.EditableBook;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverride;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverrides;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import com.albertoventurini.rosiesbooks.library.persistence.MetadataOverrideService;
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
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;

@Path("/books/{id}/edit")
class BookEditResource {
  private final CurrentUserProvider currentUsers;
  private final EditableBookCatalog books;
  private final MetadataOverrideService edits;
  private final ManualBookEntryValidator validator;

  BookEditResource(CurrentUserProvider currentUsers, EditableBookCatalog books, MetadataOverrideService edits,
      Clock clock,
      @ConfigProperty(name = "rosies-books.default-zone", defaultValue = "Africa/Johannesburg") String defaultZone) {
    this.currentUsers = currentUsers;
    this.books = books;
    this.edits = edits;
    this.validator = new ManualBookEntryValidator(clock, ZoneId.of(defaultZone));
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance form(@PathParam("id") String rawId) {
    CurrentUser owner = requireCurrentUser();
    UserEditionId id = parse(rawId);
    EditableBook book = find(owner, id);
    return render(owner, id, BookEditForm.from(book.effectiveMetadata(), book.privateNotes(), book.overrides()));
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response submit(@PathParam("id") String rawId, @RestForm String intent,
      @RestForm String title, @RestForm List<String> authors, @RestForm String subtitle, @RestForm String format,
      @RestForm String isbn10, @RestForm String isbn13, @RestForm String publisher,
      @RestForm String publicationDate, @RestForm String pageCount, @RestForm String language,
      @RestForm String description, @RestForm String notes) {
    CurrentUser owner = requireCurrentUser();
    UserEditionId id = parse(rawId);
    EditableBook book = find(owner, id);
    BookEditForm submitted = BookEditForm.from(book.effectiveMetadata(), book.privateNotes(), book.overrides())
        .submitted(title, authors, subtitle, format, isbn10, isbn13, publisher, publicationDate, pageCount,
            language, description, notes);
    String requested = intent == null ? "" : intent;
    if (requested.equals("add-author")) return ok(owner, id, submitted.addAuthor());
    if (requested.startsWith("remove-author-")) {
      int index = authorIndex(requested);
      return index >= 0 && index < submitted.bibliography().authors().size()
          ? ok(owner, id, submitted.removeAuthor(index)) : badRequest(owner, id, submitted, "Choose one of the available form actions.");
    }
    if (requested.startsWith("reset-")) {
      MetadataOverrides reset = reset(book.overrides(), requested.substring("reset-".length()));
      if (reset == null) return badRequest(owner, id, submitted, "Choose one of the available form actions.");
      if (!edits.save(owner, id, reset, book.privateNotes())) throw notFound();
      return Response.seeOther(URI.create(detailRoute(id) + "?notice=details-updated")).build();
    }
    if (!requested.equals("save")) return badRequest(owner, id, submitted, "Choose one of the available form actions.");

    ManualBookValidation validation = validator.validate(submitted.bibliography());
    BookEditForm prepared = submitted.withBibliography(validation.form());
    String normalizedNotes = normalizeNotes(notes);
    if (normalizedNotes != null && normalizedNotes.length() > 10_000) {
      prepared = prepared.withErrors(merge(prepared.errors(), "notes", "Private notes must be 10000 characters or fewer."));
    }
    if (!validation.valid() || prepared.hasError("notes")) return badRequest(owner, id, prepared, null);
    EditionMetadata metadata = validation.draft().orElseThrow().metadata();
    MetadataOverrides proposed = proposed(book, metadata);
    if (!edits.save(owner, id, proposed, normalizedNotes)) throw notFound();
    return Response.seeOther(URI.create(detailRoute(id) + "?notice=details-updated")).build();
  }

  private EditableBook find(CurrentUser owner, UserEditionId id) { return books.find(owner, id).orElseThrow(BookEditResource::notFound); }
  private TemplateInstance render(CurrentUser owner, UserEditionId id, BookEditForm form) { return BookEditTemplates.edit(new BookEditPage(owner.displayLabel(), route(id), detailRoute(id), form)); }
  private Response ok(CurrentUser owner, UserEditionId id, BookEditForm form) { return Response.ok(render(owner, id, form)).build(); }
  private Response badRequest(CurrentUser owner, UserEditionId id, BookEditForm form, String error) {
    if (error != null) form = form.withErrors(merge(form.errors(), "form", error));
    return Response.status(Response.Status.BAD_REQUEST).entity(render(owner, id, form)).build();
  }

  private static MetadataOverrides proposed(EditableBook book, EditionMetadata submitted) {
    EditionMetadata effective = book.effectiveMetadata();
    MetadataOverrides old = book.overrides();
    return new MetadataOverrides(
        choice(old.title(), effective.title(), submitted.title()),
        choiceOptional(old.subtitle(), effective.subtitle(), submitted.subtitle()),
        choice(old.authors(), effective.authors(), submitted.authors()),
        choiceOptional(old.format(), effective.format(), submitted.format()),
        choiceOptional(old.isbn10(), effective.isbn10(), submitted.isbn10()),
        choiceOptional(old.isbn13(), effective.isbn13(), submitted.isbn13()),
        choiceOptional(old.publisher(), effective.publisher(), submitted.publisher()),
        choiceOptional(old.publicationDate(), effective.publicationDate(), submitted.publicationDate()),
        choiceOptional(old.pageCount(), effective.pageCount(), submitted.pageCount()),
        choiceOptional(old.language(), effective.language(), submitted.language()),
        choiceOptional(old.description(), effective.description(), submitted.description()));
  }
  private static <T> MetadataOverride<T> choice(MetadataOverride<T> old, T effective, T submitted) {
    if (old.isInherited() && java.util.Objects.equals(effective, submitted)) return MetadataOverride.inherited();
    return MetadataOverride.value(submitted);
  }
  private static <T> MetadataOverride<T> choiceOptional(
      MetadataOverride<T> old, java.util.Optional<T> effective, java.util.Optional<T> submitted) {
    if (old.isInherited() && effective.equals(submitted)) return MetadataOverride.inherited();
    return submitted.<MetadataOverride<T>>map(MetadataOverride::value).orElseGet(MetadataOverride::blank);
  }
  private static MetadataOverrides reset(MetadataOverrides o, String field) {
    MetadataOverride<?> inherited = MetadataOverride.inherited();
    return switch (field) {
      case "title" -> new MetadataOverrides((MetadataOverride<String>) inherited,o.subtitle(),o.authors(),o.format(),o.isbn10(),o.isbn13(),o.publisher(),o.publicationDate(),o.pageCount(),o.language(),o.description());
      case "subtitle" -> new MetadataOverrides(o.title(),(MetadataOverride<String>) inherited,o.authors(),o.format(),o.isbn10(),o.isbn13(),o.publisher(),o.publicationDate(),o.pageCount(),o.language(),o.description());
      case "authors" -> new MetadataOverrides(o.title(),o.subtitle(),(MetadataOverride<List<String>>) inherited,o.format(),o.isbn10(),o.isbn13(),o.publisher(),o.publicationDate(),o.pageCount(),o.language(),o.description());
      case "format" -> new MetadataOverrides(o.title(),o.subtitle(),o.authors(),(MetadataOverride<String>) inherited,o.isbn10(),o.isbn13(),o.publisher(),o.publicationDate(),o.pageCount(),o.language(),o.description());
      case "isbn10" -> new MetadataOverrides(o.title(),o.subtitle(),o.authors(),o.format(),(MetadataOverride) inherited,o.isbn13(),o.publisher(),o.publicationDate(),o.pageCount(),o.language(),o.description());
      case "isbn13" -> new MetadataOverrides(o.title(),o.subtitle(),o.authors(),o.format(),o.isbn10(),(MetadataOverride) inherited,o.publisher(),o.publicationDate(),o.pageCount(),o.language(),o.description());
      case "publisher" -> new MetadataOverrides(o.title(),o.subtitle(),o.authors(),o.format(),o.isbn10(),o.isbn13(),(MetadataOverride<String>) inherited,o.publicationDate(),o.pageCount(),o.language(),o.description());
      case "publication-date" -> new MetadataOverrides(o.title(),o.subtitle(),o.authors(),o.format(),o.isbn10(),o.isbn13(),o.publisher(),(MetadataOverride) inherited,o.pageCount(),o.language(),o.description());
      case "page-count" -> new MetadataOverrides(o.title(),o.subtitle(),o.authors(),o.format(),o.isbn10(),o.isbn13(),o.publisher(),o.publicationDate(),(MetadataOverride<Integer>) inherited,o.language(),o.description());
      case "language" -> new MetadataOverrides(o.title(),o.subtitle(),o.authors(),o.format(),o.isbn10(),o.isbn13(),o.publisher(),o.publicationDate(),o.pageCount(),(MetadataOverride<String>) inherited,o.description());
      case "description" -> new MetadataOverrides(o.title(),o.subtitle(),o.authors(),o.format(),o.isbn10(),o.isbn13(),o.publisher(),o.publicationDate(),o.pageCount(),o.language(),(MetadataOverride<String>) inherited);
      default -> null;
    };
  }
  private static String normalizeNotes(String notes) { String value = notes == null ? "" : notes.replace("\r\n", "\n").replace('\r', '\n'); return value.strip().isEmpty() ? null : value; }
  private static Map<String,List<String>> merge(Map<String,List<String>> errors, String field, String message) { java.util.LinkedHashMap<String,List<String>> result = new java.util.LinkedHashMap<>(errors); result.put(field, List.of(message)); return result; }
  private static int authorIndex(String intent) { try { return Integer.parseInt(intent.substring("remove-author-".length())); } catch (NumberFormatException e) { return -1; } }
  private CurrentUser requireCurrentUser() { return currentUsers.currentUser().orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build())); }
  private static UserEditionId parse(String raw) { try { UUID id = UUID.fromString(raw == null ? "" : raw); if (!id.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException(); return new UserEditionId(id); } catch (IllegalArgumentException e) { throw notFound(); } }
  private static String route(UserEditionId id) { return "/books/" + id.value() + "/edit"; }
  private static String detailRoute(UserEditionId id) { return "/books/" + id.value(); }
  private static WebApplicationException notFound() { return new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build()); }
}
