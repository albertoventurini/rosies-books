package com.albertoventurini.rosiesbooks.library.imports;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.library.persistence.CoverFetchTaskService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/imports/goodreads")
class GoodreadsImportResource {
  private static final long MAX_BYTES = 5L * 1024 * 1024;
  private final CurrentUserProvider currentUsers;
  private final GoodreadsImportService imports;
  private final CoverFetchTaskService coverTasks;
  private final GoodreadsCsvParser parser = new GoodreadsCsvParser();

  GoodreadsImportResource(
      CurrentUserProvider currentUsers,
      GoodreadsImportService imports,
      CoverFetchTaskService coverTasks) {
    this.currentUsers = currentUsers;
    this.imports = imports;
    this.coverTasks = coverTasks;
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance form() {
    return GoodreadsImportTemplates.goodreads(
        new GoodreadsImportPage(
            requireUser().displayLabel(), UUID.randomUUID().toString(), List.of(), null, null));
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.TEXT_HTML)
  public Response submit(@RestForm String requestId, @RestForm FileUpload file) {
    CurrentUser owner = requireUser();
    UUID id;
    try {
      id = UUID.fromString(requestId);
    } catch (RuntimeException exception) {
      return invalid(
          owner,
          requestId,
          List.of("This import request is not valid."),
          Response.Status.BAD_REQUEST);
    }
    if (file == null || file.uploadedFile() == null)
      return invalid(
          owner,
          requestId,
          List.of("Choose a Goodreads CSV file to upload."),
          Response.Status.BAD_REQUEST);
    try {
      long size = Files.size(file.uploadedFile());
      if (size == 0)
        return invalid(
            owner, requestId, List.of("The uploaded file is empty."), Response.Status.BAD_REQUEST);
      if (size > MAX_BYTES)
        return invalid(
            owner,
            requestId,
            List.of("The uploaded file is larger than 5 MB."),
            Response.Status.REQUEST_ENTITY_TOO_LARGE);
      byte[] bytes = Files.readAllBytes(file.uploadedFile());
      String csv = new String(bytes, StandardCharsets.UTF_8);
      if (!csv.isEmpty() && csv.charAt(0) == '\uFEFF') csv = csv.substring(1);
      GoodreadsCsvParser.GoodreadsParseResult parsed = parser.parse(csv);
      if (!parsed.valid())
        return invalid(owner, requestId, parsed.errors(), Response.Status.BAD_REQUEST);
      GoodreadsImportService.GoodreadsImportResult result =
          imports.importRows(owner, id, parsed.rows());
      return Response.seeOther(URI.create("/imports/goodreads/" + result.requestId())).build();
    } catch (IOException exception) {
      return invalid(
          owner,
          requestId,
          List.of("The uploaded file could not be read."),
          Response.Status.BAD_REQUEST);
    }
  }

  @GET
  @Path("{requestId}")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance result(@PathParam("requestId") String requestId) {
    UUID id;
    try {
      id = UUID.fromString(requestId);
    } catch (RuntimeException exception) {
      throw new NotFoundException();
    }
    CurrentUser owner = requireUser();
    GoodreadsImportService.GoodreadsImportResult result =
        imports.find(owner, id).orElseThrow(NotFoundException::new);
    return GoodreadsImportTemplates.goodreads(
        new GoodreadsImportPage(
            owner.displayLabel(),
            id.toString(),
            List.of(),
            result,
            coverTasks.progress(owner, id)));
  }

  private CurrentUser requireUser() {
    return currentUsers
        .currentUser()
        .orElseThrow(
            () ->
                new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build()));
  }

  private static Response invalid(
      CurrentUser owner, String requestId, List<String> errors, Response.Status status) {
    return Response.status(status)
        .entity(
            GoodreadsImportTemplates.goodreads(
                new GoodreadsImportPage(
                    owner.displayLabel(), requestId == null ? "" : requestId, errors, null, null)))
        .build();
  }
}

record GoodreadsImportPage(
    String userDisplayLabel,
    String requestId,
    List<String> errors,
    GoodreadsImportService.GoodreadsImportResult result,
    CoverFetchTaskService.Progress coverProgress) {
  public boolean hasResult() {
    return result != null;
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public boolean hasCoverProgress() {
    return coverProgress != null;
  }

  public boolean refreshCoverProgress() {
    return coverProgress != null && coverProgress.outstanding();
  }

  public String productName() {
    return "Rosie's books";
  }
}

@CheckedTemplate(basePath = "library/imports")
class GoodreadsImportTemplates {
  static native TemplateInstance goodreads(GoodreadsImportPage page);
}
