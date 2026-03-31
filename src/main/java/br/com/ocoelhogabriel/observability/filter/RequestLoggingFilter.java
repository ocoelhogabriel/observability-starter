package br.com.ocoelhogabriel.observability.filter;

import br.com.ocoelhogabriel.observability.core.ObservableLogger;
import br.com.ocoelhogabriel.observability.core.ObservableLoggerFactory;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Filtro HTTP que loga automaticamente request e response com informações
 * de rastreio (URI, método, status, duração).
 * <p>
 * Headers sensíveis (Authorization, Cookie) são parcialmente mascarados.
 * O body não é logado por padrão para evitar overhead em payloads grandes.
 * <p>
 * Configurável via {@code ocoelhogabriel.observability.request-logging.*} no application.yaml.
 *
 * @since 1.0.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter implements Filter {

  private static final ObservableLogger log = ObservableLoggerFactory.getLogger(RequestLoggingFilter.class);

  private static final Set<String> SENSITIVE_HEADERS = Set.of(
      "authorization", "cookie", "set-cookie", "x-api-key"
  );

  private final List<String> includeHeaders;
  private final boolean logBody;

  public RequestLoggingFilter(List<String> includeHeaders, boolean logBody) {
    this.includeHeaders = includeHeaders != null ? includeHeaders : List.of();
    this.logBody = logBody;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    long startTime = System.nanoTime();
    String method = httpRequest.getMethod();
    String uri = httpRequest.getRequestURI();
    String queryString = httpRequest.getQueryString();
    String fullUri = queryString != null ? uri + "?" + queryString : uri;

    // Log request
    if (log.isDebugEnabled()) {
      StringBuilder reqLog = new StringBuilder();
      reqLog.append("▶ ").append(method).append(" ").append(fullUri);

      if (!includeHeaders.isEmpty()) {
        reqLog.append(" headers={");
        for (int i = 0; i < includeHeaders.size(); i++) {
          String headerName = includeHeaders.get(i);
          String headerValue = httpRequest.getHeader(headerName);
          if (headerValue != null) {
            if (i > 0) reqLog.append(", ");
            reqLog.append(headerName).append("=");
            if (SENSITIVE_HEADERS.contains(headerName.toLowerCase())) {
              reqLog.append(maskHeaderValue(headerValue));
            } else {
              reqLog.append(headerValue);
            }
          }
        }
        reqLog.append("}");
      }

      log.debug(reqLog.toString());
    }

    try {
      chain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startTime) / 1_000_000;
      int status = httpResponse.getStatus();

      if (status >= 500) {
        log.operation("HTTP_RESPONSE")
            .durationMs(durationMs)
            .field("method", method)
            .field("uri", fullUri)
            .field("status", String.valueOf(status))
            .error("◀ {} {} → {} ({}ms)", method, fullUri, status, durationMs);
      } else if (status >= 400) {
        log.operation("HTTP_RESPONSE")
            .durationMs(durationMs)
            .field("method", method)
            .field("uri", fullUri)
            .field("status", String.valueOf(status))
            .warn("◀ {} {} → {} ({}ms)", method, fullUri, status, durationMs);
      } else {
        log.operation("HTTP_RESPONSE")
            .durationMs(durationMs)
            .field("method", method)
            .field("uri", fullUri)
            .field("status", String.valueOf(status))
            .info("◀ {} {} → {} ({}ms)", method, fullUri, status, durationMs);
      }
    }
  }

  private String maskHeaderValue(String value) {
    if (value == null || value.length() <= 8) return "***";
    return value.substring(0, 8) + "***";
  }
}
