package com.albertoventurini.rosiesbooks.platform.web;

import io.quarkus.vertx.web.Route;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;

@ApplicationScoped
class PwaResource {

  @Route(path = "/assets/manifest.webmanifest", methods = Route.HttpMethod.GET)
  void manifest(RoutingContext context) throws IOException {
    try (InputStream resource =
        PwaResource.class.getResourceAsStream("/META-INF/resources/assets/manifest.webmanifest")) {
      context
          .response()
          .putHeader("Content-Type", "application/manifest+json")
          .end(Buffer.buffer(resource.readAllBytes()));
    }
  }
}
