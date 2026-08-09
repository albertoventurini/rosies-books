package com.albertoventurini.rosiesbooks.library.web;

record BookEditPage(
    String userDisplayLabel, String actionUrl, String detailUrl, BookEditForm form) {
  public String productName() {
    return "Rosie's books";
  }
}
