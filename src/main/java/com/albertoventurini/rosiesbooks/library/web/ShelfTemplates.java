package com.albertoventurini.rosiesbooks.library.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate(basePath = "library/web")
class ShelfTemplates {

  static native TemplateInstance shelf(ShelfPage page);

  static native TemplateInstance search(SearchPage page);
}

@CheckedTemplate(basePath = "library/web")
class ManualBookTemplates {

  static native TemplateInstance manual(ManualBookPage page);
}

@CheckedTemplate(basePath = "library/web")
class ProviderBookTemplates {
  static native TemplateInstance add(ProviderAddBookPage page);
}

@CheckedTemplate(basePath = "library/web")
class StateChangeTemplates {

  static native TemplateInstance state(StateChangePage page);
}

@CheckedTemplate(basePath = "library/web")
class BookDeletionTemplates {

  static native TemplateInstance delete(BookDeletionPage page);
}

@CheckedTemplate(basePath = "library/web")
class BookDetailTemplates {

  static native TemplateInstance detail(BookDetailPage page);
}

@CheckedTemplate(basePath = "library/web")
class BookEditTemplates {
  static native TemplateInstance edit(BookEditPage page);
}
