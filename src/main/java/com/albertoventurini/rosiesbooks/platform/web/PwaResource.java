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
    context
        .response()
        .putHeader("Content-Type", "application/manifest+json")
        .end(resource("/META-INF/resources/assets/manifest.webmanifest"));
  }

  @Route(path = "/service-worker.js", methods = Route.HttpMethod.GET)
  void serviceWorker(RoutingContext context) throws IOException {
    context
        .response()
        .putHeader("Content-Type", "text/javascript")
        .putHeader("Cache-Control", "no-cache")
        .end(resource("/META-INF/resources/service-worker.js"));
  }

  private static Buffer resource(String resourcePath) throws IOException {
    try (InputStream resource = PwaResource.class.getResourceAsStream(resourcePath)) {
      return Buffer.buffer(resource.readAllBytes());
    }
  }
}
