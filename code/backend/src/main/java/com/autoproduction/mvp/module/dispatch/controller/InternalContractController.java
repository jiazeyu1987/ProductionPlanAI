package com.autoproduction.mvp.module.dispatch.controller;

/**
 * Internal contract base path holder.
 *
 * <p>Endpoints are split by domain into dedicated controllers under the same package, to keep each controller small
 * while preserving the external routes and response structure.
 */
public final class InternalContractController {
  public static final String BASE_PATH = "/internal/v1/internal";

  private InternalContractController() {}
}

