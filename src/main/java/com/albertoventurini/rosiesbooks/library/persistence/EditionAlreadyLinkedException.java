package com.albertoventurini.rosiesbooks.library.persistence;

final class EditionAlreadyLinkedException extends RuntimeException {

  EditionAlreadyLinkedException(Throwable cause) {
    super("Edition is already linked by this user", cause);
  }
}
