package com.albertoventurini.rosiesbooks.identity.persistence;

final class DuplicateOidcIdentityException extends RuntimeException {

  DuplicateOidcIdentityException(Throwable cause) {
    super("OIDC identity is already registered", cause);
  }
}
