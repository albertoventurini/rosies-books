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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenLibraryIsbnEditionLookupTest {
  private HttpServer server;
  private final List<LogRecord> records = new CopyOnWriteArrayList<>();
  private final Handler handler =
      new Handler() {
        @Override
        public void publish(LogRecord record) {
          records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.start();
    Logger.getLogger(OpenLibraryIsbnEditionLookup.class.getName()).addHandler(handler);
  }

  @AfterEach
  void stopServer() {
    Logger.getLogger(OpenLibraryIsbnEditionLookup.class.getName()).removeHandler(handler);
    server.stop(0);
  }

  @Test
  void logsLookupExceptionsWithoutLoggingTheProviderResponse() {
    String privateResponse = "private-provider-response";
    server.createContext("/search.json", exchange -> respond(exchange, 200, privateResponse));

    assertInstanceOf(
        IsbnLookupResult.MalformedResponse.class, lookup().lookup(new Isbn13("9780306406157")));

    assertEquals(1, records.size());
    assertEquals(
        "open_library_isbn_lookup_failed"
            + " exception_class=com.fasterxml.jackson.core.JsonParseException",
        records.getFirst().getMessage());
    org.junit.jupiter.api.Assertions.assertFalse(
        records.getFirst().getMessage().contains(privateResponse));
  }

  @Test
  void logsFullLookupExceptionDetailsWhenEnabled() {
    String providerResponse = "diagnostic-provider-response";
    server.createContext("/search.json", exchange -> respond(exchange, 200, providerResponse));

    assertInstanceOf(
        IsbnLookupResult.MalformedResponse.class, lookup(true).lookup(new Isbn13("9780306406157")));

    assertEquals(1, records.size());
    assertEquals("Open Library ISBN lookup failed", records.getFirst().getMessage());
    org.junit.jupiter.api.Assertions.assertTrue(
        records.getFirst().getThrown().getMessage().contains("diagnostic"));
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
  void usesCoverEditionWhenSearchOmitsEditionKeys() {
    server.createContext(
        "/search.json",
        exchange ->
            respond(
                exchange,
                200,
                "{\"docs\":[{\"cover_edition_key\":\"OL32025351M\",\"title\":\"Year of"
                    + " Yes\",\"author_name\":[\"Shonda Rhimes\"]}]}"));
    server.createContext(
        "/books/OL32025351M.json",
        exchange ->
            respond(exchange, 200, "{\"title\":\"Year of Yes\",\"isbn_13\":[\"9781471157325\"]}"));

    IsbnLookupResult.Found found =
        assertInstanceOf(
            IsbnLookupResult.Found.class, lookup().lookup(new Isbn13("9781471157325")));

    assertEquals("OL32025351M", found.edition().providerEditionId());
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
    return lookup(false);
  }

  private OpenLibraryIsbnEditionLookup lookup(boolean logFullDetails) {
    return new OpenLibraryIsbnEditionLookup(
        HttpClient.newHttpClient(),
        new ObjectMapper(),
        java.net.URI.create("http://localhost:" + server.getAddress().getPort()),
        Duration.ofSeconds(2),
        "test@example.test",
        3,
        logFullDetails);
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws java.io.IOException {
    exchange.sendResponseHeaders(
        status, body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    exchange.getResponseBody().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    exchange.close();
  }
}
