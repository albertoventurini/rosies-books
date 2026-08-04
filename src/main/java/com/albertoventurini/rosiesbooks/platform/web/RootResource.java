package com.albertoventurini.rosiesbooks.platform.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.net.URI;

@Path("/")
class RootResource {

  @GET
  public Response index() {
    return Response.seeOther(URI.create("/reading")).build();
  }
}
