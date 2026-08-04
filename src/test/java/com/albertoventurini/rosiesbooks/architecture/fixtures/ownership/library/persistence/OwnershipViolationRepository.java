package com.albertoventurini.rosiesbooks.architecture.fixtures.ownership.library.persistence;

import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;

public class OwnershipViolationRepository {

  public boolean delete(UserEditionId id) {
    return false;
  }
}
