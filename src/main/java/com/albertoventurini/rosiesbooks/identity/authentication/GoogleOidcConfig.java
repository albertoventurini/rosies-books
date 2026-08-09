package com.albertoventurini.rosiesbooks.identity.authentication;

import io.smallrye.config.ConfigMapping;
import java.util.List;

@ConfigMapping(prefix = "rosies-books.oidc")
interface GoogleOidcConfig {

  boolean enabled();

  List<String> allowedEmails();
}
