package br.com.ocoelhogabriel.observability.metrics;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logback appender que intercepta TODOS os log events e emite métricas
 * no Micrometer, independente de o logger ser o nosso {@code ObservableLogger}
 * ou qualquer outro (Spring, Hibernate, terceiros).
 * <p>
 * Métricas emitidas:
 * <ul>
 *   <li>{@code ocoelhogabriel.log.total{level=ERROR}} — Counter global por nível</li>
 *   <li>{@code ocoelhogabriel.log.total{level=WARN}} — Counter global por nível</li>
 * </ul>
 * <p>
 * Apenas ERROR e WARN são contabilizados para não sobrecarregar o registry
 * com métricas de alto volume (INFO/DEBUG/TRACE).
 * <p>
 * Este appender é registrado programaticamente pelo auto-configuration.
 *
 * @since 1.0.0
 */
public class LogMetricsAppender extends AppenderBase<ILoggingEvent> {

  private final MeterRegistry meterRegistry;
  private final Map<String, Counter> counters = new ConcurrentHashMap<>();

  public LogMetricsAppender(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    setName("METRICS");
  }

  @Override
  protected void append(ILoggingEvent event) {
    if (meterRegistry == null) return;

    Level level = event.getLevel();

    // Só conta ERROR e WARN para não explodir cardinalidade
    if (level == Level.ERROR || level == Level.WARN) {
      String levelName = level.toString();
      String loggerName = shortenLoggerName(event.getLoggerName());
      String key = levelName + ":" + loggerName;

      counters.computeIfAbsent(key, k ->
          Counter.builder("ocoelhogabriel.log.total")
              .description("Total log events by level and logger")
              .tag("level", levelName)
              .tag("logger", loggerName)
              .register(meterRegistry)
      ).increment();
    }
  }

  /**
   * Encurta o nome do logger para reduzir cardinalidade.
   * Ex.: "br.com.ocoelhogabriel.ms_iam.auth.service.AuthService" → "AuthService"
   */
  private String shortenLoggerName(String loggerName) {
    if (loggerName == null) return "unknown";
    int lastDot = loggerName.lastIndexOf('.');
    return lastDot >= 0 ? loggerName.substring(lastDot + 1) : loggerName;
  }
}
