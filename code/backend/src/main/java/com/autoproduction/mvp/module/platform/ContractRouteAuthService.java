package com.autoproduction.mvp.module.platform;

import com.autoproduction.mvp.api.ApiSupport;
import org.springframework.stereotype.Service;

@Service
public class ContractRouteAuthService {

  public boolean shouldAuthenticate(String method, String path) {
    if ("OPTIONS".equalsIgnoreCase(method)) {
      return false;
    }
    return ApiSupport.isContractRoute(path);
  }

  public boolean hasValidBearerToken(String authorizationHeader) {
    return authorizationHeader != null && authorizationHeader.startsWith("Bearer ");
  }
}
