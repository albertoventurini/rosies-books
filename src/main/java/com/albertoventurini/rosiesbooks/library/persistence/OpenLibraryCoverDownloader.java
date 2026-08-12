package com.albertoventurini.rosiesbooks.library.persistence;

import com.albertoventurini.rosiesbooks.provider.api.TrustedCoverReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Downloads only adapter-issued Open Library or Google Books cover references. */
@ApplicationScoped
class OpenLibraryCoverDownloader {
  static final int MAX_BYTES = 5 * 1024 * 1024;
  static final long MAX_PIXELS = 12_000_000L;
  private static final int MAX_REDIRECTS = 3;
  private final HttpClient client;

  @Inject
  OpenLibraryCoverDownloader() {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  OpenLibraryCoverDownloader(HttpClient client) {
    this.client = client;
  }

  Result download(TrustedCoverReference reference) {
    try {
      URI uri = reference.value();
      if (!safe(uri, false)) return new Result.Failed();
      for (int redirects = 0; ; redirects++) {
        HttpResponse<InputStream> response = send(uri);
        if (response.statusCode() == 200) {
          try (InputStream stream = response.body()) {
            return decode(bounded(stream));
          }
        }
        URI target = response.headers().firstValue("Location").map(uri::resolve).orElse(null);
        try (InputStream ignored = response.body()) {
          if (!redirect(response.statusCode())
              || redirects >= MAX_REDIRECTS
              || target == null
              || !safe(target, true)) return new Result.Failed();
        }
        uri = target;
      }
    } catch (IOException failure) {
      return new Result.Failed();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return new Result.Failed();
    } catch (RuntimeException failure) {
      return new Result.Failed();
    }
  }

  private HttpResponse<InputStream> send(URI uri) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
  }

  private static boolean redirect(int status) {
    return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
  }

  private static boolean safe(URI uri, boolean redirected) throws IOException {
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || !(redirected ? redirectedHost(uri.getHost()) : initialHost(uri.getHost()))
        || uri.getUserInfo() != null
        || uri.getPort() != -1) return false;
    InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
    return addresses.length > 0
        && java.util.Arrays.stream(addresses).noneMatch(OpenLibraryCoverDownloader::prohibited);
  }

  private static boolean archiveHost(String host) {
    return host != null
        && ("archive.org".equalsIgnoreCase(host)
            || host.toLowerCase(Locale.ROOT).endsWith(".archive.org"));
  }

  private static boolean initialHost(String host) {
    return "covers.openlibrary.org".equalsIgnoreCase(host)
        || "books.google.com".equalsIgnoreCase(host);
  }

  private static boolean redirectedHost(String host) {
    return archiveHost(host) || "books.google.com".equalsIgnoreCase(host);
  }

  private static boolean prohibited(InetAddress address) {
    byte[] bytes = address.getAddress();
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) return true;
    if (bytes.length == 4) {
      int first = Byte.toUnsignedInt(bytes[0]), second = Byte.toUnsignedInt(bytes[1]);
      return first == 0
          || first >= 224
          || (first == 100 && second >= 64 && second <= 127)
          || (first == 192 && (second == 0 || second == 2 || second == 88 || second == 168))
          || (first == 198 && (second == 18 || second == 19 || second == 51))
          || (first == 203 && second == 0);
    }
    return (bytes[0] & (byte) 0xfe) == (byte) 0xfc // unique-local
        || (bytes[0] == 0x20
            && bytes[1] == 0x01
            && bytes[2] == 0x0d
            && bytes[3] == (byte) 0xb8); // documentation
  }

  private static byte[] bounded(InputStream source) throws IOException {
    byte[] buffer = new byte[8192];
    java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
    for (int count; (count = source.read(buffer)) != -1; ) {
      if (result.size() + count > MAX_BYTES) throw new IOException("cover too large");
      result.write(buffer, 0, count);
    }
    return result.toByteArray();
  }

  private static Result decode(byte[] content) throws IOException {
    try (ImageInputStream input =
        ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
      java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) return new Result.Failed();
      ImageReader reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        String mime =
            switch (format) {
              case "jpeg", "jpg" -> "image/jpeg";
              case "png" -> "image/png";
              default -> null;
            };
        if (mime == null) return new Result.Failed();
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS)
          return new Result.Failed();
        BufferedImage image = reader.read(0);
        if (image == null) return new Result.Failed();
        return new Result.Success(content, mime, width, height);
      } finally {
        reader.dispose();
      }
    }
  }

  sealed interface Result {
    record Success(byte[] content, String mimeType, int width, int height) implements Result {
      public Success {
        content = content.clone();
      }

      @Override
      public byte[] content() {
        return content.clone();
      }
    }

    record Failed() implements Result {}
  }
}
