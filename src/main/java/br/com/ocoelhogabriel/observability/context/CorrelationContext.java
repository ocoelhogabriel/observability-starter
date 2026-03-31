package br.com.ocoelhogabriel.observability.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Contexto de correlação imutável que carrega metadados de rastreio
 * através de toda a cadeia de processamento (HTTP → Async → Kafka → Scheduler).
 * <p>
 * Este é o coração do sistema de observabilidade. Cada campo é automaticamente
 * propagado para o MDC do SLF4J e para headers HTTP de saída.
 *
 * @since 1.0.0
 */
public final class CorrelationContext {

  private final String traceId;
  private final String spanId;
  private final String parentSpanId;
  private final String tenantId;
  private final String userId;
  private final String requestId;
  private final String scope;
  private final String operationId;
  private final String sessionId;
  private final String serviceName;
  private final Map<String, String> customFields;

  private CorrelationContext(Builder builder) {
    this.traceId = builder.traceId != null ? builder.traceId : generateShortId();
    this.spanId = builder.spanId != null ? builder.spanId : generateShortId();
    this.parentSpanId = builder.parentSpanId;
    this.tenantId = builder.tenantId;
    this.userId = builder.userId;
    this.requestId = builder.requestId;
    this.scope = builder.scope;
    this.operationId = builder.operationId;
    this.sessionId = builder.sessionId;
    this.serviceName = builder.serviceName;
    this.customFields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.customFields));
  }

  // ── Getters ──────────────────────────────────────────────────────

  public String traceId() { return traceId; }
  public String spanId() { return spanId; }
  public String parentSpanId() { return parentSpanId; }
  public String tenantId() { return tenantId; }
  public String userId() { return userId; }
  public String requestId() { return requestId; }
  public String scope() { return scope; }
  public String operationId() { return operationId; }
  public String sessionId() { return sessionId; }
  public String serviceName() { return serviceName; }
  public Map<String, String> customFields() { return customFields; }

  // ── Factory Methods ──────────────────────────────────────────────

  /** Cria um contexto vazio com traceId e spanId gerados automaticamente. */
  public static CorrelationContext empty() {
    return new Builder().build();
  }

  /** Cria um novo builder. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Cria um novo contexto filho, herdando traceId e metadados do pai
   * mas gerando um novo spanId (com parentSpanId apontando para o span atual).
   */
  public CorrelationContext createChildSpan() {
    return new Builder()
        .traceId(this.traceId)
        .parentSpanId(this.spanId)
        .tenantId(this.tenantId)
        .userId(this.userId)
        .requestId(this.requestId)
        .sessionId(this.sessionId)
        .serviceName(this.serviceName)
        .customFields(this.customFields)
        .build();
  }

  /** Cria um builder a partir deste contexto (para derivar novos contextos). */
  public Builder toBuilder() {
    Builder b = new Builder();
    b.traceId = this.traceId;
    b.spanId = this.spanId;
    b.parentSpanId = this.parentSpanId;
    b.tenantId = this.tenantId;
    b.userId = this.userId;
    b.requestId = this.requestId;
    b.scope = this.scope;
    b.operationId = this.operationId;
    b.sessionId = this.sessionId;
    b.serviceName = this.serviceName;
    b.customFields.putAll(this.customFields);
    return b;
  }

  /**
   * Converte para Map plano (para popular o MDC de uma vez).
   * Só inclui chaves não-nulas.
   */
  public Map<String, String> toMdcMap() {
    Map<String, String> map = new LinkedHashMap<>();
    putIfPresent(map, "traceId", traceId);
    putIfPresent(map, "spanId", spanId);
    putIfPresent(map, "parentSpanId", parentSpanId);
    putIfPresent(map, "tenantId", tenantId);
    putIfPresent(map, "userId", userId);
    putIfPresent(map, "requestId", requestId);
    putIfPresent(map, "scope", scope);
    putIfPresent(map, "operationId", operationId);
    putIfPresent(map, "sessionId", sessionId);
    putIfPresent(map, "serviceName", serviceName);
    map.putAll(customFields);
    return Collections.unmodifiableMap(map);
  }

  private static void putIfPresent(Map<String, String> map, String key, String value) {
    if (value != null && !value.isEmpty()) {
      map.put(key, value);
    }
  }

  private static String generateShortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  @Override
  public String toString() {
    return "CorrelationContext{traceId='%s', spanId='%s', scope='%s', tenant='%s', user='%s'}"
        .formatted(traceId, spanId, scope, tenantId, userId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CorrelationContext that)) return false;
    return Objects.equals(traceId, that.traceId) && Objects.equals(spanId, that.spanId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(traceId, spanId);
  }

  // ── Builder ──────────────────────────────────────────────────────

  public static final class Builder {

    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String tenantId;
    private String userId;
    private String requestId;
    private String scope;
    private String operationId;
    private String sessionId;
    private String serviceName;
    private final Map<String, String> customFields = new LinkedHashMap<>();

    private Builder() {}

    public Builder traceId(String traceId) { this.traceId = traceId; return this; }
    public Builder spanId(String spanId) { this.spanId = spanId; return this; }
    public Builder parentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; return this; }
    public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
    public Builder userId(String userId) { this.userId = userId; return this; }
    public Builder requestId(String requestId) { this.requestId = requestId; return this; }
    public Builder scope(String scope) { this.scope = scope; return this; }
    public Builder operationId(String operationId) { this.operationId = operationId; return this; }
    public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
    public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }

    public Builder customField(String key, String value) {
      if (key != null && value != null) {
        this.customFields.put(key, value);
      }
      return this;
    }

    public Builder customFields(Map<String, String> fields) {
      if (fields != null) {
        this.customFields.putAll(fields);
      }
      return this;
    }

    public CorrelationContext build() {
      return new CorrelationContext(this);
    }
  }
}
