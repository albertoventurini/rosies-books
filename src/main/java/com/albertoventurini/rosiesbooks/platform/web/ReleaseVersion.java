package com.albertoventurini.rosiesbooks.platform.web;

import io.quarkus.qute.TemplateGlobal;
import org.eclipse.microprofile.config.ConfigProvider;

@TemplateGlobal
final class ReleaseVersion {

  private ReleaseVersion() {}

  static String releaseVersion() {
    return ConfigProvider.getConfig().getValue("rosies-books.release-version", String.class);
  }
}
