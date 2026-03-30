package com.autoproduction.mvp.core;

import java.time.LocalDate;

final class MvpStoreMasterdataPatchDateSupport {
  private MvpStoreMasterdataPatchDateSupport() {}

  static LocalDate parseConfigDate(MvpStoreMasterdataPatchSupport store, String dateText) {
    try {
      return LocalDate.parse(dateText.trim());
    } catch (RuntimeException ex) {
      throw store.badRequest("Invalid date format: " + dateText);
    }
  }
}

