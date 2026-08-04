package com.albertoventurini.rosiesbooks.identity.web;

import java.util.List;

record DevelopmentUsersPage(List<DevelopmentUserOption> users, String error) {

  DevelopmentUsersPage {
    users = List.copyOf(users);
  }
}

record DevelopmentUserOption(String alias, String displayLabel, boolean selected) {}
