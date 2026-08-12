package com.albertoventurini.rosiesbooks.provider.googlebooks;

import com.albertoventurini.rosiesbooks.provider.api.Isbn10;
import com.albertoventurini.rosiesbooks.provider.api.Isbn13;
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
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

/** Google Books HTTP/JSON adapter; Google representations remain in this package. */
@ApplicationScoped
public class GoogleBooksIsbnEditionLookup {
  private static final Logger LOG = Logger.getLogger(GoogleBooksIsbnEditionLookup.class);
  private final HttpClient client;
  private final ObjectMapper json;
  private final URI baseUrl;
  private final Duration requestTimeout;
  private final String apiKey;
  private final boolean logFullExceptionDetails;

  @Inject
  GoogleBooksIsbnEditionLookup(GoogleBooksConfig config, ObjectMapper json) {
    this(
        HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build(),
        json,
        config.baseUrl(),
        config.connectTimeout(),
        config.requestTimeout(),
        config.apiKey(),
        config.logFullExceptionDetails());
  }

  GoogleBooksIsbnEditionLookup(
      HttpClient client,
      ObjectMapper json,
      URI baseUrl,
      Duration connectTimeout,
      Duration requestTimeout,
      String apiKey,
      boolean logFullExceptionDetails) {
    if (apiKey == null || apiKey.isBlank())
      throw new IllegalArgumentException("Google Books API key is required");
    this.client = client;
    this.json = json;
    this.baseUrl = baseUrl.toString().endsWith("/") ? baseUrl : URI.create(baseUrl + "/");
    this.requestTimeout = requestTimeout;
    this.apiKey = apiKey;
    this.logFullExceptionDetails = logFullExceptionDetails;
  }

  public IsbnLookupResult lookup(Isbn13 isbn) {
    try {
      HttpResponse<String> response = send(isbn);
      IsbnLookupResult outcome = responseOutcome(response);
      if (outcome != null) return outcome;
      for (JsonNode volume : json.readTree(response.body()).path("items")) {
        IsbnLookupResult selected = selectedEdition(isbn, volume);
        if (selected instanceof IsbnLookupResult.Found) return selected;
      }
      return new IsbnLookupResult.NotFound();
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

  private HttpResponse<String> send(Isbn13 isbn) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(
                baseUrl.resolve(
                    "volumes?q="
                        + encoded("isbn:" + isbn.value())
                        + "&printType=books&maxResults=10&key="
                        + encoded(apiKey)))
            .timeout(requestTimeout)
            .GET()
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 500)
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return response;
  }

  private void logFailure(Exception failure) {
    if (logFullExceptionDetails) {
      LOG.warn("Google Books ISBN lookup failed", failure);
    } else {
      LOG.warnf("google_books_isbn_lookup_failed exception_class=%s", failure.getClass().getName());
    }
  }

  private static IsbnLookupResult responseOutcome(HttpResponse<String> response) {
    return switch (response.statusCode()) {
      case 200 -> null;
      case 429 ->
          new IsbnLookupResult.RateLimited(
              retryAfter(response.headers().firstValue("Retry-After")));
      default -> new IsbnLookupResult.Unavailable();
    };
  }

  private static IsbnLookupResult selectedEdition(Isbn13 requested, JsonNode volume) {
    JsonNode info = volume.path("volumeInfo");
    Optional<Isbn10> isbn10 = isbn10(info.path("industryIdentifiers"));
    Optional<Isbn13> isbn13 = isbn13(info.path("industryIdentifiers"));
    if (!matches(requested, isbn10, isbn13)) return new IsbnLookupResult.NotFound();
    String id = text(volume.path("id"));
    String title = text(info.path("title"));
    List<String> authors = strings(info.path("authors"));
    if (id == null || title == null || authors.isEmpty())
      return new IsbnLookupResult.MalformedResponse();
    return new IsbnLookupResult.Found(
        new SelectedEdition(
            "googlebooks",
            id,
            title,
            optionalText(info.path("subtitle")),
            authors,
            Optional.empty(),
            optionalText(info.path("publisher")),
            publicationDate(info.path("publishedDate")),
            positiveInteger(info.path("pageCount")),
            optionalText(info.path("language")),
            optionalText(info.path("description")),
            isbn10,
            isbn13,
            cover(info.path("imageLinks"))));
  }

  private static boolean matches(
      Isbn13 requested, Optional<Isbn10> isbn10, Optional<Isbn13> isbn13) {
    return isbn13.map(value -> value.value().equals(requested.value())).orElse(false)
        || isbn10.map(value -> value.toIsbn13().value().equals(requested.value())).orElse(false);
  }

  private static Optional<Isbn10> isbn10(JsonNode values) {
    return identifier(values, "ISBN_10", Isbn10::new);
  }

  private static Optional<Isbn13> isbn13(JsonNode values) {
    return identifier(values, "ISBN_13", Isbn13::new);
  }

  private static <T> Optional<T> identifier(
      JsonNode values, String type, java.util.function.Function<String, T> parser) {
    for (JsonNode value : values) {
      if (type.equals(text(value.path("type")))) {
        try {
          return Optional.of(parser.apply(text(value.path("identifier")).replaceAll("[-\\s]", "")));
        } catch (RuntimeException ignored) {
          return Optional.empty();
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<TrustedCoverReference> cover(JsonNode links) {
    for (String size :
        List.of("extraLarge", "large", "medium", "small", "thumbnail", "smallThumbnail")) {
      String value = text(links.path(size));
      if (value == null) continue;
      try {
        return Optional.of(new TrustedCoverReference(httpsGoogleBooksUrl(value)));
      } catch (IllegalArgumentException ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private static URI httpsGoogleBooksUrl(String value) {
    URI uri = URI.create(value);
    if ("http".equalsIgnoreCase(uri.getScheme())
        && "books.google.com".equalsIgnoreCase(uri.getHost()))
      return URI.create("https" + value.substring(4));
    return uri;
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
        java.util.regex.Pattern.compile("^(\\d{4})(?:-(\\d{2})(?:-(\\d{2}))?)?$").matcher(value);
    if (!match.matches()) return Optional.empty();
    try {
      int year = Integer.parseInt(match.group(1));
      if (match.group(2) == null) return Optional.of(PartialPublicationDate.year(year));
      if (match.group(3) == null)
        return Optional.of(
            PartialPublicationDate.yearMonth(year, Integer.parseInt(match.group(2))));
      return Optional.of(
          PartialPublicationDate.full(
              year, Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3))));
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }

  private static List<String> strings(JsonNode values) {
    java.util.ArrayList<String> result = new java.util.ArrayList<>();
    for (JsonNode value : values) {
      String text = text(value);
      if (text != null) result.add(text);
    }
    return result;
  }

  private static String text(JsonNode node) {
    return node != null && node.isTextual() && !node.asText().isBlank()
        ? node.asText().strip()
        : null;
  }

  private static Optional<String> optionalText(JsonNode node) {
    return Optional.ofNullable(text(node));
  }

  private static String encoded(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
}
