package com.albertoventurini.rosiesbooks.provider.openlibrary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.albertoventurini.rosiesbooks.provider.api.Isbn13;
import com.albertoventurini.rosiesbooks.provider.api.IsbnLookupResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenLibraryIsbnEditionLookupTest {
  private HttpServer server;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void returnsOnlyAnEditionWhoseIdentifiersContainTheRequestedIsbn() {
    server.createContext(
        "/search.json",
        exchange ->
            respond(
                exchange,
                200,
                "{\"docs\":[{\"edition_key\":[\"OL1M\"],\"title\":\"Example\",\"author_name\":[\"Author\"]}]}"));
    server.createContext(
        "/books/OL1M.json",
        exchange ->
            respond(
                exchange,
                200,
                "{\"title\":\"Example\",\"isbn_10\":[\"0306406152\"],\"covers\":[123]}"));

    IsbnLookupResult.Found found =
        assertInstanceOf(
            IsbnLookupResult.Found.class, lookup().lookup(new Isbn13("9780306406157")));

    assertEquals("openlibrary", found.edition().providerName());
    assertEquals("OL1M", found.edition().providerEditionId());
    assertEquals("Example", found.edition().title());
    assertEquals(
        "https://covers.openlibrary.org/b/id/123-L.jpg",
        found.edition().cover().orElseThrow().value().toString());
  }

  @Test
  void rejectsCandidateWhenEditionDoesNotContainRequestedIsbn() {
    server.createContext(
        "/search.json",
        exchange ->
            respond(
                exchange,
                200,
                "{\"docs\":[{\"edition_key\":[\"OL2M\"],\"title\":\"Example\",\"author_name\":[\"Author\"]}]}"));
    server.createContext(
        "/books/OL2M.json",
        exchange -> respond(exchange, 200, "{\"isbn_13\":[\"9781861972712\"]}"));

    assertInstanceOf(IsbnLookupResult.NotFound.class, lookup().lookup(new Isbn13("9780306406157")));
  }

  @Test
  void returnsRateLimitWithoutRetryingAndParsesRetryAfter() {
    AtomicInteger requests = new AtomicInteger();
    server.createContext(
        "/search.json",
        exchange -> {
          requests.incrementAndGet();
          exchange.getResponseHeaders().add("Retry-After", "12");
          respond(exchange, 429, "");
        });

    IsbnLookupResult.RateLimited limited =
        assertInstanceOf(
            IsbnLookupResult.RateLimited.class, lookup().lookup(new Isbn13("9780306406157")));

    assertEquals(Duration.ofSeconds(12), limited.retryAfter().orElseThrow());
    assertEquals(1, requests.get());
  }

  @Test
  void retriesOneServerFailure() {
    AtomicInteger requests = new AtomicInteger();
    server.createContext(
        "/search.json",
        exchange -> {
          if (requests.incrementAndGet() == 1) respond(exchange, 500, "");
          else respond(exchange, 200, "{\"docs\":[]}");
        });

    assertInstanceOf(IsbnLookupResult.NotFound.class, lookup().lookup(new Isbn13("9780306406157")));
    assertEquals(2, requests.get());
  }

  private OpenLibraryIsbnEditionLookup lookup() {
    return new OpenLibraryIsbnEditionLookup(
        HttpClient.newHttpClient(),
        new ObjectMapper(),
        java.net.URI.create("http://localhost:" + server.getAddress().getPort()),
        Duration.ofSeconds(2),
        "test@example.test",
        3);
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws java.io.IOException {
    exchange.sendResponseHeaders(
        status, body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    exchange.getResponseBody().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    exchange.close();
  }
}
