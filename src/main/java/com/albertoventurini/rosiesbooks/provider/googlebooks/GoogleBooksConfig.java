package com.albertoventurini.rosiesbooks.provider.googlebooks;

import io.smallrye.config.ConfigMapping;
import java.net.URI;
import java.time.Duration;

@ConfigMapping(prefix = "rosies-books.google-books")
interface GoogleBooksConfig {
  URI baseUrl();

  String apiKey();

  Duration connectTimeout();

  Duration requestTimeout();

  boolean logFullExceptionDetails();
}
