package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_PREFERENCE;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.jooq.DSLContext;

@ApplicationScoped
class UserPreferenceRepository {

  private final DSLContext dsl;

  UserPreferenceRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  void save(CurrentUser owner, LibraryLayout layout) {
    dsl.insertInto(USER_PREFERENCE)
        .set(USER_PREFERENCE.USER_ID, owner.id().value())
        .set(USER_PREFERENCE.LAYOUT, layout.name())
        .onConflict(USER_PREFERENCE.USER_ID)
        .doUpdate()
        .set(USER_PREFERENCE.LAYOUT, layout.name())
        .execute();
  }

  Optional<LibraryLayout> find(CurrentUser owner) {
    return dsl.select(USER_PREFERENCE.LAYOUT)
        .from(USER_PREFERENCE)
        .where(USER_PREFERENCE.USER_ID.eq(owner.id().value()))
        .fetchOptional(value -> LibraryLayout.valueOf(value.value1()));
  }
}
