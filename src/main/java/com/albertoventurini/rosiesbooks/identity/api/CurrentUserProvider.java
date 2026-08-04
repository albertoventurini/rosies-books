package com.albertoventurini.rosiesbooks.identity.api;

import java.util.Optional;

/** Replaceable boundary for resolving the current request's authenticated application user. */
public interface CurrentUserProvider {

  Optional<CurrentUser> currentUser();
}
