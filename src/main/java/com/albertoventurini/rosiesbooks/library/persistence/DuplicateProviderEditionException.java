package com.albertoventurini.rosiesbooks.library.persistence;

final class DuplicateProviderEditionException extends RuntimeException {

  DuplicateProviderEditionException(Throwable cause) {
    super("Provider edition identity is already in use", cause);
  }
}
