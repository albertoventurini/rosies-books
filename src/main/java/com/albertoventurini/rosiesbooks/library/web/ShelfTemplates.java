package com.albertoventurini.rosiesbooks.library.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate(basePath = "library/web")
class ShelfTemplates {

  static native TemplateInstance shelf(ShelfPage page);
}
