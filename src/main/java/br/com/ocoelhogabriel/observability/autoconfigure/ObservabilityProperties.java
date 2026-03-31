package br.com.ocoelhogabriel.observability.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuração para o Ocoelhogabriel Observability Starter.
 * <p>
 * Configuradas via {@code ocoelhogabriel.observability.*} no application.yaml.
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ocoelhogabriel.observability")
public class ObservabilityProperties {

  /** Ativa ou desativa o starter inteiro. Default: true. */
  private boolean enabled = true;

  /** Nome do serviço (auto-detectado via spring.application.name se vazio). */
  private String serviceName;

  private final RequestLogging requestLogging = new RequestLogging();
  private final Masking masking = new Masking();
  private final Sampling sampling = new Sampling();
  private final Audit audit = new Audit();
  private final Metrics metrics = new Metrics();
  private final Health health = new Health();

  // ── Getters & Setters ─────────────────────────────────────────────

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getServiceName() { return serviceName; }
  public void setServiceName(String serviceName) { this.serviceName = serviceName; }
  public RequestLogging getRequestLogging() { return requestLogging; }
  public Masking getMasking() { return masking; }
  public Sampling getSampling() { return sampling; }
  public Audit getAudit() { return audit; }
  public Metrics getMetrics() { return metrics; }
  public Health getHealth() { return health; }

  // ── Request Logging ───────────────────────────────────────────────

  public static class RequestLogging {
    private boolean enabled = true;
    private List<String> includeHeaders = List.of("Content-Type", "Accept", "User-Agent");
    private boolean logBody = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getIncludeHeaders() { return includeHeaders; }
    public void setIncludeHeaders(List<String> includeHeaders) { this.includeHeaders = includeHeaders; }
    public boolean isLogBody() { return logBody; }
    public void setLogBody(boolean logBody) { this.logBody = logBody; }
  }

  // ── Masking ───────────────────────────────────────────────────────

  public static class Masking {
    private boolean enabled = true;
    private boolean includeDefaults = true;
    private List<PatternConfig> patterns = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isIncludeDefaults() { return includeDefaults; }
    public void setIncludeDefaults(boolean includeDefaults) { this.includeDefaults = includeDefaults; }
    public List<PatternConfig> getPatterns() { return patterns; }
    public void setPatterns(List<PatternConfig> patterns) { this.patterns = patterns; }
  }

  public static class PatternConfig {
    private String name;
    private String regex;
    private String replacement;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRegex() { return regex; }
    public void setRegex(String regex) { this.regex = regex; }
    public String getReplacement() { return replacement; }
    public void setReplacement(String replacement) { this.replacement = replacement; }
  }

  // ── Sampling ──────────────────────────────────────────────────────

  public static class Sampling {
    private boolean enabled = false;
    private double debugRate = 0.1;
    private double traceRate = 0.01;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double getDebugRate() { return debugRate; }
    public void setDebugRate(double debugRate) { this.debugRate = debugRate; }
    public double getTraceRate() { return traceRate; }
    public void setTraceRate(double traceRate) { this.traceRate = traceRate; }
  }

  // ── Audit ─────────────────────────────────────────────────────────

  public static class Audit {
    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
  }

  // ── Metrics ───────────────────────────────────────────────────────

  public static class Metrics {
    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
  }

  // ── Health ────────────────────────────────────────────────────────

  public static class Health {
    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
  }
}
