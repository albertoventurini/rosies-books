package com.albertoventurini.rosiesbooks.library.persistence;

import org.jooq.exception.DataAccessException;
import org.postgresql.util.PSQLException;

final class PostgresConstraint {

  private PostgresConstraint() {}

  static boolean isUniqueViolation(DataAccessException failure, String constraint) {
    PSQLException postgres = failure.getCause(PSQLException.class);
    return postgres != null
        && "23505".equals(postgres.getSQLState())
        && postgres.getServerErrorMessage() != null
        && constraint.equals(postgres.getServerErrorMessage().getConstraint());
  }
}
