package br.com.ocoelhogabriel.observability.audit;

import br.com.ocoelhogabriel.observability.masking.DataMasker;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Logger especializado para eventos de auditoria.
 * <p>
 * Emite logs num logger SLF4J separado ({@code audit.<serviceName>})
 * que pode ser roteado para um appender/índice dedicado no logback-spring.xml.
 * <p>
 * Cada evento de auditoria é emitido em nível INFO com todos os campos
 * do {@link AuditEvent} injetados temporariamente no MDC, permitindo
 * que o logstash-logback-encoder os inclua como campos JSON de primeiro nível.
 * <p>
 * <b>Uso:</b>
 * <pre>{@code
 * @Autowired
 * private AuditLogger auditLogger;
 *
 * auditLogger.log(AuditEvent.builder()
 *     .action("USER_LOGIN")
 *     .actor(userId)
 *     .target("Session", sessionId)
 *     .tenant(tenantId)
 *     .outcome(AuditEvent.Outcome.SUCCESS)
 *     .detail("Login via SSO")
 *     .build());
 * }</pre>
 *
 * @since 1.0.0
 */
public class AuditLogger {

  private final Logger logger;
  private final DataMasker dataMasker;

  public AuditLogger(String serviceName, DataMasker dataMasker) {
    this.logger = LoggerFactory.getLogger("audit." + (serviceName != null ? serviceName : "app"));
    this.dataMasker = dataMasker;
  }

  public AuditLogger(String serviceName) {
    this(serviceName, null);
  }

  /**
   * Emite um evento de auditoria.
   */
  public void log(AuditEvent event) {
    if (event == null) return;

    Map<String, String> mdcFields = event.toMdcMap();
    mdcFields.forEach(MDC::put);

    try {
      String message = buildMessage(event);
      if (dataMasker != null) {
        message = dataMasker.mask(message);
      }
      logger.info(message);
    } finally {
      mdcFields.keySet().forEach(MDC::remove);
    }
  }

  /**
   * Emite um evento de auditoria de falha com exceção.
   */
  public void logFailure(AuditEvent event, Throwable t) {
    if (event == null) return;

    Map<String, String> mdcFields = event.toMdcMap();
    mdcFields.forEach(MDC::put);

    try {
      String message = buildMessage(event);
      if (dataMasker != null) {
        message = dataMasker.mask(message);
      }
      logger.warn(message, t);
    } finally {
      mdcFields.keySet().forEach(MDC::remove);
    }
  }

  private String buildMessage(AuditEvent event) {
    StringBuilder sb = new StringBuilder();
    sb.append("AUDIT | ").append(event.action());
    sb.append(" | outcome=").append(event.outcome());

    if (event.actor() != null) {
      sb.append(" | actor=").append(event.actor());
    }
    if (event.targetType() != null) {
      sb.append(" | target=").append(event.targetType());
      if (event.targetId() != null) {
        sb.append("/").append(event.targetId());
      }
    }
    if (event.tenantId() != null) {
      sb.append(" | tenant=").append(event.tenantId());
    }
    if (event.detail() != null) {
      sb.append(" | ").append(event.detail());
    }
    return sb.toString();
  }
}
