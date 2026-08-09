package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.persistence.CoverAssetDeliveryService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@Path("/covers")
class CoverAssetResource {
  private final CoverAssetDeliveryService covers;

  CoverAssetResource(CoverAssetDeliveryService covers) {
    this.covers = covers;
  }

  @GET
  @Path("{sha256}")
  public Response cover(@PathParam("sha256") String sha256) {
    var cover =
        covers
            .find(sha256)
            .orElseThrow(() -> new WebApplicationException(Response.status(404).build()));
    return Response.ok(cover.content(), cover.mimeType())
        .header("ETag", '"' + sha256 + '"')
        .header("Cache-Control", "public, max-age=31536000, immutable")
        .build();
  }
}
