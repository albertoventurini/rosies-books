package com.albertoventurini.rosiesbooks.library.persistence;

import org.jooq.exception.DataAccessException;
import org.postgresql.util.PSQLException;

final class PostgresConstraint {

  private PostgresConstraint() {}

  static boolean isUniqueViolation(DataAccessException failure, String constraint) {
    return isViolation(failure, "23505", constraint);
  }

  static boolean isCheckViolation(DataAccessException failure, String constraint) {
    return isViolation(failure, "23514", constraint);
  }

  private static boolean isViolation(
      DataAccessException failure, String sqlState, String constraint) {
    PSQLException postgres = failure.getCause(PSQLException.class);
    return postgres != null
        && sqlState.equals(postgres.getSQLState())
        && postgres.getServerErrorMessage() != null
        && constraint.equals(postgres.getServerErrorMessage().getConstraint());
  }
}
