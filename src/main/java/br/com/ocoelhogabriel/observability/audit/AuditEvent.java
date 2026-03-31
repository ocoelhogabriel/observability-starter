package br.com.ocoelhogabriel.observability.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Evento de auditoria com schema fixo para compliance e rastreabilidade.
 * <p>
 * Cada evento de auditoria contém: ação, ator, alvo, tenant, outcome,
 * detalhes e timestamp. É emitido pelo {@link AuditLogger} em um logger
 * SLF4J separado ({@code audit.*}), permitindo roteamento para índice
 * dedicado (Elasticsearch {@code audit-*}, tabela de banco, etc.).
 * <p>
 * <b>Exemplo:</b>
 * <pre>{@code
 * AuditEvent event = AuditEvent.builder()
 *     .action("USER_LOGIN")
 *     .actor("user-123")
 *     .target("Session", "session-456")
 *     .tenant("acme-corp")
 *     .outcome(AuditEvent.Outcome.SUCCESS)
 *     .detail("IP: 192.168.1.1")
 *     .build();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class AuditEvent {

  public enum Outcome {
    SUCCESS, FAILURE, DENIED, SKIPPED
  }

  private final String action;
  private final String actor;
  private final String targetType;
  private final String targetId;
  private final String tenantId;
  private final Outcome outcome;
  private final String detail;
  private final Instant timestamp;
  private final Map<String, String> metadata;

  private AuditEvent(Builder builder) {
    this.action = builder.action;
    this.actor = builder.actor;
    this.targetType = builder.targetType;
    this.targetId = builder.targetId;
    this.tenantId = builder.tenantId;
    this.outcome = builder.outcome != null ? builder.outcome : Outcome.SUCCESS;
    this.detail = builder.detail;
    this.timestamp = Instant.now();
    this.metadata = Map.copyOf(builder.metadata);
  }

  // ── Getters ──────────────────────────────────────────────────────

  public String action()     { return action; }
  public String actor()      { return actor; }
  public String targetType() { return targetType; }
  public String targetId()   { return targetId; }
  public String tenantId()   { return tenantId; }
  public Outcome outcome()   { return outcome; }
  public String detail()     { return detail; }
  public Instant timestamp() { return timestamp; }
  public Map<String, String> metadata() { return metadata; }

  /**
   * Converte para Map para injeção no MDC.
   */
  public Map<String, String> toMdcMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("audit.action", action);
    if (actor != null)      map.put("audit.actor", actor);
    if (targetType != null) map.put("audit.targetType", targetType);
    if (targetId != null)   map.put("audit.targetId", targetId);
    if (tenantId != null)   map.put("audit.tenantId", tenantId);
    map.put("audit.outcome", outcome.name());
    if (detail != null)     map.put("audit.detail", detail);
    map.put("audit.timestamp", timestamp.toString());
    metadata.forEach((k, v) -> map.put("audit." + k, v));
    return map;
  }

  // ── Builder ──────────────────────────────────────────────────────

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String action;
    private String actor;
    private String targetType;
    private String targetId;
    private String tenantId;
    private Outcome outcome;
    private String detail;
    private final Map<String, String> metadata = new LinkedHashMap<>();

    private Builder() {}

    public Builder action(String action) { this.action = action; return this; }
    public Builder actor(String actor)   { this.actor = actor; return this; }

    public Builder target(String type, String id) {
      this.targetType = type;
      this.targetId = id;
      return this;
    }

    public Builder tenant(String tenantId) { this.tenantId = tenantId; return this; }
    public Builder outcome(Outcome outcome) { this.outcome = outcome; return this; }
    public Builder detail(String detail) { this.detail = detail; return this; }

    public Builder meta(String key, String value) {
      this.metadata.put(key, value);
      return this;
    }

    public AuditEvent build() {
      if (action == null || action.isBlank()) {
        throw new IllegalArgumentException("AuditEvent.action is required");
      }
      return new AuditEvent(this);
    }
  }
}
