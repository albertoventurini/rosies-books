package com.albertoventurini.rosiesbooks.platform.web;

import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
class RootResource {

  private static final FoundationPage PAGE =
      new FoundationPage("Rosie's books", "A quiet, private place to keep track of your reading.");

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance index() {
    return WebTemplates.foundation(PAGE);
  }
}
