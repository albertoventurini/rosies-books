package com.albertoventurini.rosiesbooks.library.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate(basePath = "library/web")
class ShelfTemplates {

  static native TemplateInstance shelf(ShelfPage page);
}

@CheckedTemplate(basePath = "library/web")
class ManualBookTemplates {

  static native TemplateInstance manual(ManualBookPage page);

  static native TemplateInstance manualReview(ManualBookReviewPage page);
}
