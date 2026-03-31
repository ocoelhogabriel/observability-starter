package br.com.ocoelhogabriel.observability.autoconfigure;

import br.com.ocoelhogabriel.observability.aop.ObservedAspect;
import br.com.ocoelhogabriel.observability.audit.AuditLogger;
import br.com.ocoelhogabriel.observability.context.ContextPropagator;
import br.com.ocoelhogabriel.observability.core.ObservableLogger;
import br.com.ocoelhogabriel.observability.filter.CorrelationFilter;
import br.com.ocoelhogabriel.observability.filter.RequestLoggingFilter;
import br.com.ocoelhogabriel.observability.health.LoggingHealthIndicator;
import br.com.ocoelhogabriel.observability.masking.DataMasker;
import br.com.ocoelhogabriel.observability.masking.MaskingMessageConverter;
import br.com.ocoelhogabriel.observability.masking.MaskingPattern;
import br.com.ocoelhogabriel.observability.metrics.LogMetricsAppender;
import br.com.ocoelhogabriel.observability.sampling.LogSampler;
import ch.qos.logback.classic.LoggerContext;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskDecorator;

/**
 * Auto-configuration principal do Ocoelhogabriel Observability Starter.
 * <p>
 * Registra automaticamente todos os componentes da biblioteca como beans Spring,
 * cada um condicionado via {@code @ConditionalOnProperty} para controle granular.
 * <p>
 * Basta adicionar a dependência Maven e os componentes são ativados automaticamente.
 * Para desativar individuamente:
 * <pre>{@code
 * ocoelhogabriel:
 *   observability:
 *     enabled: true
 *     masking:
 *       enabled: false
 *     sampling:
 *       enabled: false
 * }</pre>
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ocoelhogabriel.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

  private final ObservabilityProperties properties;
  private final Environment environment;

  public ObservabilityAutoConfiguration(ObservabilityProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  private String resolveServiceName() {
    String name = properties.getServiceName();
    if (name != null && !name.isBlank()) return name;
    return environment.getProperty("spring.application.name", "app");
  }

  // ── Correlation Filter ────────────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean(CorrelationFilter.class)
  @ConditionalOnClass(name = "jakarta.servlet.Filter")
  public CorrelationFilter correlationFilter() {
    return new CorrelationFilter(resolveServiceName());
  }

  // ── Request Logging Filter ────────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean(RequestLoggingFilter.class)
  @ConditionalOnClass(name = "jakarta.servlet.Filter")
  @ConditionalOnProperty(prefix = "ocoelhogabriel.observability.request-logging", name = "enabled",
      havingValue = "true", matchIfMissing = true)
  public RequestLoggingFilter requestLoggingFilter() {
    ObservabilityProperties.RequestLogging rl = properties.getRequestLogging();
    return new RequestLoggingFilter(rl.getIncludeHeaders(), rl.isLogBody());
  }

  // ── Data Masker ───────────────────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean(DataMasker.class)
  @ConditionalOnProperty(prefix = "ocoelhogabriel.observability.masking", name = "enabled",
      havingValue = "true", matchIfMissing = true)
  public DataMasker dataMasker() {
    ObservabilityProperties.Masking masking = properties.getMasking();

    List<MaskingPattern> customPatterns = masking.getPatterns().stream()
        .map(p -> new MaskingPattern(p.getName(), p.getRegex(), p.getReplacement()))
        .toList();

    DataMasker masker = new DataMasker(customPatterns, masking.isIncludeDefaults());

    // Registra globalmente
    ObservableLogger.setDataMasker(masker);
    MaskingMessageConverter.setSharedMasker(masker);

    return masker;
  }

  // ── Context Propagator (TaskDecorator para @Async) ────────────────

  @Bean
  @ConditionalOnMissingBean(ContextPropagator.class)
  public TaskDecorator contextPropagator() {
    return new ContextPropagator();
  }

  // ── @Observed AOP Aspect ──────────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean(ObservedAspect.class)
  @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
  public ObservedAspect observedAspect(MeterRegistry meterRegistry) {
    return new ObservedAspect(meterRegistry);
  }

  // ── Audit Logger ──────────────────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean(AuditLogger.class)
  @ConditionalOnProperty(prefix = "ocoelhogabriel.observability.audit", name = "enabled",
      havingValue = "true", matchIfMissing = true)
  public AuditLogger auditLogger(DataMasker dataMasker) {
    return new AuditLogger(resolveServiceName(), dataMasker);
  }

  // ── Logging Health Indicator ──────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean(LoggingHealthIndicator.class)
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  @ConditionalOnProperty(prefix = "ocoelhogabriel.observability.health", name = "enabled",
      havingValue = "true", matchIfMissing = true)
  public LoggingHealthIndicator loggingHealthIndicator() {
    return new LoggingHealthIndicator();
  }

  // ── Métricas + Sampling (configuração programática do Logback) ────

  @Bean
  @ConditionalOnProperty(prefix = "ocoelhogabriel.observability.metrics", name = "enabled",
      havingValue = "true", matchIfMissing = true)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  public LogMetricsAppender logMetricsAppender(MeterRegistry meterRegistry) {
    if (LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext) {
      LogMetricsAppender appender = new LogMetricsAppender(meterRegistry);
      appender.setContext(loggerContext);
      appender.start();

      ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(
          ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
      rootLogger.addAppender(appender);

      return appender;
    }
    return new LogMetricsAppender(meterRegistry);
  }

  @PostConstruct
  public void configureSampling() {
    ObservabilityProperties.Sampling sampling = properties.getSampling();
    if (!sampling.isEnabled()) return;

    if (LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext) {
      LogSampler sampler = new LogSampler();
      sampler.setDebugRate(sampling.getDebugRate());
      sampler.setTraceRate(sampling.getTraceRate());
      sampler.setContext(loggerContext);
      sampler.start();
      loggerContext.addTurboFilter(sampler);
    }
  }



  @Bean
  MeterRegistryConfigurer meterRegistryConfigurer(MeterRegistry meterRegistry) {
    return new MeterRegistryConfigurer(meterRegistry);
  }

  /**
   * Bean interno que registra o MeterRegistry globalmente no ObservableLogger
   * assim que ele estiver disponível.
   */
  static class MeterRegistryConfigurer {
    MeterRegistryConfigurer(MeterRegistry meterRegistry) {
      ObservableLogger.setMeterRegistry(meterRegistry);
    }
  }
}
