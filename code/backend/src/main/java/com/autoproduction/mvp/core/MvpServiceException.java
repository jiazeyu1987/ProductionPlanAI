package com.autoproduction.mvp.core;

public class MvpServiceException extends RuntimeException {
  private final int statusCode;
  private final String code;
  private final boolean retryable;

  public MvpServiceException(int statusCode, String code, String message, boolean retryable) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
    this.retryable = retryable;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getCode() {
    return code;
  }

  public boolean isRetryable() {
    return retryable;
  }
}
