package com.albertoventurini.rosiesbooks.provider.openlibrary;

import io.smallrye.config.ConfigMapping;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

@ConfigMapping(prefix = "provider.open-library")
interface OpenLibraryConfig {
  URI baseUrl();

  Optional<String> operatorContact();

  Duration connectTimeout();

  Duration requestTimeout();

  int requestsPerSecond();

  boolean logFullExceptionDetails();
}
