package com.albertoventurini.rosiesbooks.platform.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServiceWorkerPolicyTest {

  private static final Path SERVICE_WORKER =
      Path.of("src/main/resources/META-INF/resources/service-worker.js");

  @Test
  void precachesOnlyKnownStaticResourcesAndUsesTheGenericFallbackForFailedNavigations()
      throws IOException {
    String worker = Files.readString(SERVICE_WORKER);

    assertTrue(worker.contains("const PRECACHE_URLS"));
    assertTrue(worker.contains("/assets/manifest.webmanifest"));
    assertTrue(worker.contains("/assets/app.css"));
    assertTrue(worker.contains("rosies-books-static-v3"));
    assertTrue(worker.contains("/assets/icons/rosies-books-rounded-192.png"));
    assertTrue(worker.contains("/assets/icons/rosies-books-rounded-512.png"));
    assertTrue(worker.contains("/assets/fonts/newsreader-latin-v1.woff2"));
    assertTrue(worker.contains("/assets/fonts/ibm-plex-sans-latin-v1.woff2"));
    assertTrue(worker.contains("/offline.html"));
    assertTrue(worker.contains("request.mode === \"navigate\""));
    assertTrue(worker.contains("caches.match(OFFLINE_FALLBACK)"));
  }

  @Test
  void doesNotCachePrivateRoutesOrNonGetRequests() throws IOException {
    String worker = Files.readString(SERVICE_WORKER);

    assertTrue(worker.contains("request.method !== \"GET\""));
    assertTrue(worker.contains("url.pathname.startsWith(\"/oidc/\")"));
    assertTrue(worker.contains("url.pathname.startsWith(\"/covers/\")"));
    assertFalse(worker.contains("cache.put("));
    assertFalse(worker.contains("event.request.url"));
  }
}
