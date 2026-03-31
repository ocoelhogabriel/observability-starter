package br.com.ocoelhogabriel.observability.core;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Builder fluente para construir log events ricos em metadados.
 * <p>
 * Permite encadear operação, tenant, entity, métricas customizadas e
 * campos arbitrários antes de emitir o log em qualquer nível.
 * <p>
 * Todos os campos adicionados são automaticamente injetados no MDC
 * durante a emissão do log e removidos logo após, garantindo thread-safety.
 * <p>
 * <b>Exemplo:</b>
 * <pre>{@code
 * log.operation("PROCESS_PAYMENT")
 *    .tenant("acme-corp")
 *    .entity("Payment", paymentId)
 *    .field("amount", "150.00")
 *    .field("currency", "BRL")
 *    .metric("processingTimeMs", 342)
 *    .info("Pagamento processado com sucesso");
 * }</pre>
 *
 * @since 1.0.0
 */
public final class LogEvent {

  private final ObservableLogger logger;
  private final String operationName;
  private final Map<String, String> fields = new LinkedHashMap<>();

  LogEvent(ObservableLogger logger, String operationName) {
    this.logger = logger;
    this.operationName = operationName;
    this.fields.put("operation", operationName);
  }

  // ── Domain context ────────────────────────────────────────────────

  /** Define o tenant (organização/empresa) associado a este evento. */
  public LogEvent tenant(String tenantId) {
    if (tenantId != null) fields.put("tenantId", tenantId);
    return this;
  }

  /** Define o usuário que disparou a ação. */
  public LogEvent user(String userId) {
    if (userId != null) fields.put("userId", userId);
    return this;
  }

  /** Define a entidade de domínio alvo da operação (type + id). */
  public LogEvent entity(String entityType, String entityId) {
    if (entityType != null) fields.put("entityType", entityType);
    if (entityId != null) fields.put("entityId", entityId);
    return this;
  }

  /** Define o outcome da operação (SUCCESS, FAILURE, SKIPPED, etc.). */
  public LogEvent outcome(String outcome) {
    if (outcome != null) fields.put("outcome", outcome);
    return this;
  }

  // ── Métricas inline ───────────────────────────────────────────────

  /** Adiciona uma métrica numérica ao evento (emitida como campo no JSON). */
  public LogEvent metric(String name, Number value) {
    if (name != null && value != null) fields.put("metric." + name, value.toString());
    return this;
  }

  /** Adiciona a duração da operação em milissegundos. */
  public LogEvent durationMs(long durationMs) {
    fields.put("durationMs", String.valueOf(durationMs));
    return this;
  }

  // ── Campos arbitrários ────────────────────────────────────────────

  /** Adiciona um campo arbitrário key-value ao contexto do log. */
  public LogEvent field(String key, String value) {
    if (key != null && value != null) fields.put(key, value);
    return this;
  }

  /** Adiciona múltiplos campos arbitrários ao contexto do log. */
  public LogEvent fields(Map<String, String> extraFields) {
    if (extraFields != null) fields.putAll(extraFields);
    return this;
  }

  // ── Emissão ───────────────────────────────────────────────────────

  public void info(String message, Object... args) {
    emitWithContext(() -> logger.delegate().info(logger.mask(message), args), "INFO");
  }

  public void warn(String message, Object... args) {
    emitWithContext(() -> logger.delegate().warn(logger.mask(message), args), "WARN");
  }

  public void error(String message, Object... args) {
    emitWithContext(() -> logger.delegate().error(logger.mask(message), args), "ERROR");
  }

  public void error(String message, Throwable t) {
    emitWithContext(() -> logger.delegate().error(logger.mask(message), t), "ERROR");
  }

  public void debug(String message, Object... args) {
    if (logger.isDebugEnabled()) {
      emitWithContext(() -> logger.delegate().debug(logger.mask(message), args), null);
    }
  }

  public void trace(String message, Object... args) {
    if (logger.isTraceEnabled()) {
      emitWithContext(() -> logger.delegate().trace(logger.mask(message), args), null);
    }
  }

  // ── Internals ─────────────────────────────────────────────────────

  private void emitWithContext(Runnable logAction, String level) {
    // Injeta campos temporários no MDC
    fields.forEach(MDC::put);
    try {
      logAction.run();
      if (level != null) {
        logger.incrementCounter(level);
      }
    } finally {
      // Remove apenas as chaves que nós injetamos
      fields.keySet().forEach(MDC::remove);
    }
  }
}
