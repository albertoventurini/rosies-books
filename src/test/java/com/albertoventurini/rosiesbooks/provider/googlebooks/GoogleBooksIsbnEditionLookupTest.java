package com.albertoventurini.rosiesbooks.provider.googlebooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.albertoventurini.rosiesbooks.provider.api.Isbn13;
import com.albertoventurini.rosiesbooks.provider.api.IsbnLookupResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoogleBooksIsbnEditionLookupTest {
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
  void returnsOnlyAVolumeWhoseIdentifiersContainTheRequestedIsbnAndItsGoogleCover() {
    server.createContext(
        "/volumes",
        exchange ->
            respond(
                exchange,
                200,
                """
                {"items":[
                  {"id":"wrong","volumeInfo":{"title":"Wrong","authors":["Author"],"industryIdentifiers":[{"type":"ISBN_13","identifier":"9781861972712"}]}},
                  {"id":"google-volume","volumeInfo":{"title":"Example","subtitle":"Sub","authors":["Author"],"publisher":"Publisher","publishedDate":"2024-03-04","pageCount":321,"language":"en","description":"Why are some people more successful than others?<br>And why can they repeat their success?<p>Because they start with why.</p>","industryIdentifiers":[{"type":"ISBN_10","identifier":"0306406152"}],"imageLinks":{"large":"http://books.google.com/books?id=google-volume&img=1"}}}
                ]}
                """));

    IsbnLookupResult.Found found =
        assertInstanceOf(
            IsbnLookupResult.Found.class, lookup().lookup(new Isbn13("9780306406157")));

    assertEquals("googlebooks", found.edition().providerName());
    assertEquals("google-volume", found.edition().providerEditionId());
    assertEquals("Example", found.edition().title());
    assertEquals(
        "https://books.google.com/books?id=google-volume&img=1",
        found.edition().cover().orElseThrow().value().toString());
    assertEquals(
        "Why are some people more successful than others?\n"
            + "And why can they repeat their success?\n\n"
            + "Because they start with why.",
        found.edition().description().orElseThrow());
  }

  @Test
  void returnsNotFoundWhenNoVolumeContainsTheRequestedIsbn() {
    server.createContext("/volumes", exchange -> respond(exchange, 200, "{\"items\":[]}"));

    assertInstanceOf(IsbnLookupResult.NotFound.class, lookup().lookup(new Isbn13("9780306406157")));
  }

  private GoogleBooksIsbnEditionLookup lookup() {
    return new GoogleBooksIsbnEditionLookup(
        HttpClient.newHttpClient(),
        new ObjectMapper(),
        URI.create("http://localhost:" + server.getAddress().getPort()),
        Duration.ofSeconds(2),
        Duration.ofSeconds(2),
        "test-api-key",
        false);
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws java.io.IOException {
    exchange.sendResponseHeaders(
        status, body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    exchange.getResponseBody().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    exchange.close();
  }
}
