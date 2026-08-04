package com.albertoventurini.rosiesbooks.library.shelves;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import java.util.List;

/** Owner-scoped shelf query boundary. */
public interface ShelfCatalog {

  List<ShelfBook> find(CurrentUser owner, Shelf shelf);
}
