package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.provider.api.SelectedEdition;

record ProviderReviewPage(
    String userDisplayLabel, SelectedEdition edition, String reviewToken, ManualBookForm form) {}

/** The deliberately non-persisting confirmation contract to be implemented in task 7-3. */
record ProviderBookReviewConfirmation(
    String reviewToken, String state, String startedOn, String finishedOn) {}
