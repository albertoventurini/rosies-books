package com.albertoventurini.rosiesbooks.platform.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate(basePath = "platform/web")
class WebTemplates {

  static native TemplateInstance error(ErrorPage page);
}
