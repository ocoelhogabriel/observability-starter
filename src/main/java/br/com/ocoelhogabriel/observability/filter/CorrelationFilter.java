package br.com.ocoelhogabriel.observability.filter;

import br.com.ocoelhogabriel.observability.context.CorrelationContext;
import br.com.ocoelhogabriel.observability.context.CorrelationContextHolder;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Filtro HTTP que cria e gerencia o {@link CorrelationContext} para cada request.
 * <p>
 * Comportamento:
 * <ol>
 *   <li>Verifica se o Micrometer Brave já injetou {@code traceId} no MDC</li>
 *   <li>Se não, lê os headers {@code X-Trace-Id}, {@code X-Request-Id}, de propagação</li>
 *   <li>Lê headers de contexto: {@code X-Tenant-Id}, {@code X-User-Id}, {@code X-Session-Id}</li>
 *   <li>Monta um {@link CorrelationContext} completo e o disponibiliza via {@link CorrelationContextHolder}</li>
 *   <li>Devolve os IDs nos response headers para rastreabilidade end-to-end</li>
 * </ol>
 * <p>
 * Registrado automaticamente no {@code HIGHEST_PRECEDENCE} para garantir que todo
 * o pipeline tenha o contexto disponível.
 *
 * @since 1.0.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter implements Filter {

  public static final String HEADER_TRACE_ID   = "X-Trace-Id";
  public static final String HEADER_REQUEST_ID = "X-Request-Id";
  public static final String HEADER_TENANT_ID  = "X-Tenant-Id";
  public static final String HEADER_USER_ID    = "X-User-Id";
  public static final String HEADER_SESSION_ID = "X-Session-Id";
  public static final String HEADER_SPAN_ID    = "X-Span-Id";

  private final String serviceName;

  public CorrelationFilter(String serviceName) {
    this.serviceName = serviceName;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    CorrelationContext context = buildContext(httpRequest);

    try {
      CorrelationContextHolder.set(context);

      // Devolve IDs nos response headers
      httpResponse.setHeader(HEADER_TRACE_ID, context.traceId());
      httpResponse.setHeader(HEADER_SPAN_ID, context.spanId());
      if (context.requestId() != null) {
        httpResponse.setHeader(HEADER_REQUEST_ID, context.requestId());
      }

      chain.doFilter(request, response);
    } finally {
      CorrelationContextHolder.clear();
    }
  }

  private CorrelationContext buildContext(HttpServletRequest request) {
    // 1. TraceId: Brave MDC > Header > Gerado
    String traceId = MDC.get("traceId");
    if (isBlank(traceId)) {
      traceId = request.getHeader(HEADER_TRACE_ID);
    }
    if (isBlank(traceId)) {
      traceId = generateShortId();
    }

    // 2. SpanId: Brave MDC > Gerado
    String spanId = MDC.get("spanId");

    // 3. RequestId do header (ou gerar um)
    String requestId = request.getHeader(HEADER_REQUEST_ID);
    if (isBlank(requestId)) {
      requestId = generateShortId();
    }

    return CorrelationContext.builder()
        .traceId(traceId)
        .spanId(spanId)
        .tenantId(request.getHeader(HEADER_TENANT_ID))
        .userId(request.getHeader(HEADER_USER_ID))
        .requestId(requestId)
        .sessionId(request.getHeader(HEADER_SESSION_ID))
        .scope("HTTP")
        .operationId(request.getMethod() + " " + request.getRequestURI())
        .serviceName(serviceName)
        .build();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String generateShortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }
}
