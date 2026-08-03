package com.albertoventurini.rosiesbooks.platform.database;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

@ApplicationScoped
class JooqContextProducer {

  @Produces
  @ApplicationScoped
  DSLContext dslContext(AgroalDataSource dataSource) {
    return DSL.using(dataSource, SQLDialect.POSTGRES);
  }
}
