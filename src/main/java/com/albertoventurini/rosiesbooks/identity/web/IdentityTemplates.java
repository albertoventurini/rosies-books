package com.albertoventurini.rosiesbooks.identity.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate(basePath = "identity/web")
class IdentityTemplates {

  static native TemplateInstance devUsers(DevelopmentUsersPage page);
}
