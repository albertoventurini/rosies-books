package com.albertoventurini.rosiesbooks.architecture.fixtures.persistence.library.application;

import java.sql.Connection;
import org.jooq.DSLContext;

public final class PersistenceViolation {

  private DSLContext jooq;
  private Connection jdbc;
}
