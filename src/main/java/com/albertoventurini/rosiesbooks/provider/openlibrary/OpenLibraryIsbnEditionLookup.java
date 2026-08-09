package com.albertoventurini.rosiesbooks.provider.openlibrary;

import com.albertoventurini.rosiesbooks.provider.api.Isbn10;
import com.albertoventurini.rosiesbooks.provider.api.Isbn13;
import com.albertoventurini.rosiesbooks.provider.api.IsbnEditionLookup;
import com.albertoventurini.rosiesbooks.provider.api.IsbnLookupResult;
import com.albertoventurini.rosiesbooks.provider.api.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.provider.api.SelectedEdition;
import com.albertoventurini.rosiesbooks.provider.api.TrustedCoverReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.jboss.logging.Logger;

/** Open Library HTTP/JSON adapter; provider representations remain in this package. */
@ApplicationScoped
public class OpenLibraryIsbnEditionLookup implements IsbnEditionLookup {
  private static final Logger LOG = Logger.getLogger(OpenLibraryIsbnEditionLookup.class);
  private final HttpClient client;
  private final ObjectMapper json;
  private final URI baseUrl;
  private final Duration requestTimeout;
  private final String userAgent;
  private final long minimumSpacingMillis;
  private final boolean logFullExceptionDetails;
  private long nextAllowedRequestMillis;

  @Inject
  OpenLibraryIsbnEditionLookup(OpenLibraryConfig config, ObjectMapper json) {
    this(
        HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build(),
        json,
        config.baseUrl(),
        config.requestTimeout(),
        config.operatorContact().orElse("development"),
        config.requestsPerSecond(),
        config.logFullExceptionDetails());
  }

  OpenLibraryIsbnEditionLookup(
      HttpClient client,
      ObjectMapper json,
      URI baseUrl,
      Duration requestTimeout,
      String contact,
      int requestsPerSecond) {
    this(client, json, baseUrl, requestTimeout, contact, requestsPerSecond, false);
  }

  OpenLibraryIsbnEditionLookup(
      HttpClient client,
      ObjectMapper json,
      URI baseUrl,
      Duration requestTimeout,
      String contact,
      int requestsPerSecond,
      boolean logFullExceptionDetails) {
    if (contact == null || contact.isBlank())
      throw new IllegalArgumentException("operator contact is required");
    if (requestsPerSecond < 1 || requestsPerSecond > 3)
      throw new IllegalArgumentException("rate must be 1..3 requests per second");
    this.client = client;
    this.json = json;
    this.baseUrl = baseUrl;
    this.requestTimeout = requestTimeout;
    this.userAgent = "RosiesBooks (" + contact + ")";
    this.minimumSpacingMillis = 1000L / requestsPerSecond;
    this.logFullExceptionDetails = logFullExceptionDetails;
  }

  @Override
  public IsbnLookupResult lookup(Isbn13 isbn) {
    try {
      HttpResponse<String> search = send("/search.json?isbn=" + encoded(isbn.value()));
      IsbnLookupResult outcome = responseOutcome(search);
      if (outcome != null) return outcome;
      JsonNode candidate = json.readTree(search.body()).path("docs").path(0);
      String editionId = text(candidate.path("edition_key").path(0));
      if (editionId == null) editionId = text(candidate.path("cover_edition_key"));
      if (editionId == null) return new IsbnLookupResult.NotFound();
      HttpResponse<String> edition = send("/books/" + encoded(editionId) + ".json");
      outcome = responseOutcome(edition);
      if (outcome != null) return outcome;
      return selectedEdition(isbn, candidate, json.readTree(edition.body()), editionId);
    } catch (IOException malformed) {
      logFailure(malformed);
      return new IsbnLookupResult.MalformedResponse();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      logFailure(interrupted);
      return new IsbnLookupResult.Unavailable();
    } catch (RuntimeException unavailable) {
      logFailure(unavailable);
      return new IsbnLookupResult.Unavailable();
    }
  }

  private void logFailure(Exception failure) {
    if (logFullExceptionDetails) {
      LOG.warn("Open Library ISBN lookup failed", failure);
      return;
    }
    LOG.warnf("open_library_isbn_lookup_failed exception_class=%s", failure.getClass().getName());
  }

  private HttpResponse<String> send(String path) throws IOException, InterruptedException {
    awaitPermit();
    HttpRequest request =
        HttpRequest.newBuilder(baseUrl.resolve(path))
            .timeout(requestTimeout)
            .header("User-Agent", userAgent)
            .GET()
            .build();
    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException transportFailure) {
      Thread.sleep(ThreadLocalRandom.current().nextLong(250, 501));
      awaitPermit();
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    if (response.statusCode() >= 500) {
      Thread.sleep(ThreadLocalRandom.current().nextLong(250, 501));
      awaitPermit();
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    return response;
  }

  private synchronized void awaitPermit() throws InterruptedException {
    long now = System.currentTimeMillis();
    long wait = nextAllowedRequestMillis - now;
    if (wait > 0) Thread.sleep(wait);
    nextAllowedRequestMillis = System.currentTimeMillis() + minimumSpacingMillis;
  }

  private static IsbnLookupResult responseOutcome(HttpResponse<String> response) {
    return switch (response.statusCode()) {
      case 200 -> null;
      case 404 -> new IsbnLookupResult.NotFound();
      case 429 ->
          new IsbnLookupResult.RateLimited(
              retryAfter(response.headers().firstValue("Retry-After")));
      default -> new IsbnLookupResult.Unavailable();
    };
  }

  private static IsbnLookupResult selectedEdition(
      Isbn13 requested, JsonNode candidate, JsonNode edition, String editionId) {
    if (!containsRequestedIsbn(edition, requested.value())) return new IsbnLookupResult.NotFound();
    String title = text(edition.path("title"));
    if (title == null) title = text(candidate.path("title"));
    List<String> authors = strings(candidate.path("author_name"));
    if (title == null || authors.isEmpty()) return new IsbnLookupResult.MalformedResponse();
    return new IsbnLookupResult.Found(
        new SelectedEdition(
            "openlibrary",
            editionId,
            title,
            optionalText(edition.path("subtitle")),
            authors,
            optionalText(edition.path("physical_format")),
            firstText(edition.path("publishers")),
            publicationDate(edition.path("publish_date")),
            positiveInteger(edition.path("number_of_pages")),
            Optional.empty(),
            Optional.empty(),
            isbn10(edition.path("isbn_10")),
            isbn13(edition.path("isbn_13")),
            cover(edition.path("covers"))));
  }

  private static boolean containsRequestedIsbn(JsonNode edition, String requested) {
    List<String> values = strings(edition.path("isbn_13"));
    values.addAll(strings(edition.path("isbn_10")));
    for (String value : values) {
      String normalized = value.replaceAll("[-\\s]", "");
      try {
        if (normalized.length() == 10) {
          String body = "978" + normalized.substring(0, 9);
          if ((body + isbn13CheckDigit(body)).equals(requested)) return true;
        } else if (new Isbn13(normalized).value().equals(requested)) return true;
      } catch (IllegalArgumentException ignored) {
        // An invalid identifier from the provider cannot establish an edition match.
      }
    }
    return false;
  }

  private static char isbn13CheckDigit(String body) {
    int sum = 0;
    for (int index = 0; index < body.length(); index++)
      sum += (body.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
    return (char) ('0' + (10 - sum % 10) % 10);
  }

  private static Optional<Duration> retryAfter(Optional<String> header) {
    try {
      return header.map(value -> Duration.ofSeconds(Long.parseLong(value)));
    } catch (RuntimeException ignored) {
      try {
        return header
            .map(
                value ->
                    Duration.between(
                        Instant.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant()))
            .map(value -> value.isNegative() ? Duration.ZERO : value);
      } catch (RuntimeException invalid) {
        return Optional.empty();
      }
    }
  }

  private static Optional<TrustedCoverReference> cover(JsonNode covers) {
    if (!covers.isArray() || covers.isEmpty() || !covers.get(0).canConvertToLong())
      return Optional.empty();
    return Optional.of(
        new TrustedCoverReference(
            URI.create(
                "https://covers.openlibrary.org/b/id/" + covers.get(0).asLong() + "-L.jpg")));
  }

  private static Optional<Integer> positiveInteger(JsonNode node) {
    return node.canConvertToInt() && node.asInt() > 0
        ? Optional.of(node.asInt())
        : Optional.empty();
  }

  private static Optional<PartialPublicationDate> publicationDate(JsonNode node) {
    String value = text(node);
    if (value == null) return Optional.empty();
    java.util.regex.Matcher match =
        java.util.regex.Pattern.compile("\\b(\\d{4})(?:-(\\d{2})(?:-(\\d{2}))?)?\\b")
            .matcher(value);
    if (!match.find()) return Optional.empty();
    try {
      int year = Integer.parseInt(match.group(1));
      if (match.group(2) == null) return Optional.of(PartialPublicationDate.year(year));
      int month = Integer.parseInt(match.group(2));
      if (match.group(3) == null) return Optional.of(PartialPublicationDate.yearMonth(year, month));
      return Optional.of(
          PartialPublicationDate.full(year, month, Integer.parseInt(match.group(3))));
    } catch (RuntimeException invalid) {
      return Optional.empty();
    }
  }

  private static Optional<Isbn10> isbn10(JsonNode node) {
    return strings(node).stream().flatMap(value -> parseIsbn10(value).stream()).findFirst();
  }

  private static Optional<Isbn13> isbn13(JsonNode node) {
    return strings(node).stream().flatMap(value -> parseIsbn13(value).stream()).findFirst();
  }

  private static Optional<Isbn10> parseIsbn10(String value) {
    try {
      return Optional.of(Isbn10.parse(value));
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }

  private static Optional<Isbn13> parseIsbn13(String value) {
    try {
      return Optional.of(new Isbn13(value.replaceAll("[-\\s]", "")));
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }

  private static Optional<String> firstText(JsonNode node) {
    return node.isArray() && !node.isEmpty() ? optionalText(node.get(0)) : Optional.empty();
  }

  private static Optional<String> optionalText(JsonNode node) {
    return Optional.ofNullable(text(node));
  }

  private static String text(JsonNode node) {
    return node != null && node.isTextual() && !node.asText().isBlank()
        ? node.asText().trim()
        : null;
  }

  private static List<String> strings(JsonNode node) {
    List<String> values = new ArrayList<>();
    if (node.isArray())
      for (JsonNode value : node) {
        String text = text(value);
        if (text != null) values.add(text);
      }
    return values;
  }

  private static String encoded(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
