package com.autoproduction.mvp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ErpKingdeeApiSession {
  private static final Logger log = LoggerFactory.getLogger(ErpKingdeeApiSession.class);

  private final ObjectMapper objectMapper;
  private final ErpSqliteOrderValidator validator;
  private final String baseUrl;
  private final String acctId;
  private final String username;
  private final String password;
  private final int lcid;
  private final int timeoutSeconds;
  private final HttpClient httpClient;

  private volatile boolean apiLoggedIn;

  ErpKingdeeApiSession(
    ObjectMapper objectMapper,
    boolean verifySsl,
    int timeoutSeconds,
    String baseUrl,
    String acctId,
    String username,
    String password,
    int lcid,
    ErpSqliteOrderValidator validator
  ) {
    this.objectMapper = objectMapper;
    this.validator = validator;
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    this.acctId = acctId == null ? "" : acctId.trim();
    this.username = username == null ? "" : username.trim();
    this.password = password == null ? "" : password;
    this.lcid = lcid;
    this.timeoutSeconds = Math.max(1, timeoutSeconds);
    this.httpClient = buildHttpClient(verifySsl, this.timeoutSeconds);
    this.apiLoggedIn = false;
  }

  void invalidateLogin() {
    apiLoggedIn = false;
  }

  List<String> serviceUrls(String serviceName) {
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return List.of(
      base + "/K3Cloud/" + serviceName,
      base + "/k3cloud/" + serviceName,
      base + "/" + serviceName
    );
  }

  Object postFormForJson(String url, Map<String, String> payload) {
    String body = encodeForm(payload);
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(timeoutSeconds))
      .header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return parseJson(response.body());
    } catch (Exception ex) {
      throw new RuntimeException("ERP http request failed: " + url, ex);
    }
  }

  synchronized void ensureApiLogin(boolean force) {
    if (!validator.hasApiConfig()) {
      throw new RuntimeException("ERP API config is missing.");
    }
    if (apiLoggedIn && !force) {
      return;
    }
    String service = "Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc";
    List<Map<String, String>> payloads = List.of(
      Map.of(
        "acctID", acctId,
        "username", username,
        "password", password,
        "lcid", String.valueOf(lcid)
      ),
      Map.of(
        "AcctID", acctId,
        "UserName", username,
        "Password", password,
        "Lcid", String.valueOf(lcid)
      )
    );

    RuntimeException lastEx = null;
    for (String url : serviceUrls(service)) {
      for (Map<String, String> payload : payloads) {
        try {
          Object parsed = postFormForJson(url, payload);
          if (parsed instanceof Map<?, ?> map) {
            Object loginResultType = map.get("LoginResultType");
            if (loginResultType instanceof Number number && number.intValue() == 1) {
              apiLoggedIn = true;
              return;
            }
            Object successByApi = map.get("IsSuccessByAPI");
            if (Boolean.TRUE.equals(successByApi)) {
              apiLoggedIn = true;
              return;
            }
            lastEx = new RuntimeException("ERP login failed: " + map);
            continue;
          }
          lastEx = new RuntimeException("ERP login response is not json object.");
        } catch (RuntimeException ex) {
          lastEx = ex;
        }
      }
    }
    if (lastEx != null) {
      throw lastEx;
    }
    throw new RuntimeException("ERP login failed.");
  }

  private String encodeForm(Map<String, String> payload) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : payload.entrySet()) {
      if (!first) {
        builder.append("&");
      }
      first = false;
      builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
      builder.append("=");
      builder.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8));
    }
    return builder.toString();
  }

  private Object parseJson(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    try {
      return objectMapper.readValue(text, Object.class);
    } catch (Exception ex) {
      return text;
    }
  }

  private static HttpClient buildHttpClient(boolean verifySsl, int timeoutSeconds) {
    CookieManager cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    HttpClient.Builder builder = HttpClient.newBuilder()
      .cookieHandler(cookieManager)
      .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)));
    if (!verifySsl) {
      try {
        TrustManager[] trustAll = new TrustManager[] {
          new X509TrustManager() {
            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
              return new java.security.cert.X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
          }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new java.security.SecureRandom());
        builder.sslContext(sslContext);
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("");
        builder.sslParameters(sslParameters);
      } catch (Exception ex) {
        log.warn("Failed to initialize insecure SSL for ERP client.", ex);
      }
    }
    return builder.build();
  }
}

