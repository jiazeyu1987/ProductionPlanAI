package com.autoproduction.mvp.module.dispatch.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.api.ContractControllerSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

final class InternalContractRequestSupport {
  private InternalContractRequestSupport() {}

  static Map<String, Object> body(Map<String, Object> payload) {
    return ContractControllerSupport.body(payload);
  }

  static String requireRequestId(HttpServletRequest request, Map<String, Object> body) {
    return ContractControllerSupport.requireRequestId(request, body);
  }

  static String getOrCreateRequestId(HttpServletRequest request) {
    return ApiSupport.getOrCreateRequestId(request, null);
  }

  static String operator(Map<String, Object> body, String defaultOperator) {
    return ContractControllerSupport.operator(body, defaultOperator);
  }
}

