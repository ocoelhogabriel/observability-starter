package br.com.ocoelhogabriel.observability.health;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * {@link HealthIndicator} que expõe a saúde do pipeline de logging
 * no endpoint {@code /actuator/health}.
 * <p>
 * Monitora:
 * <ul>
 *   <li>Utilização da fila do {@link AsyncAppender} (se presente)</li>
 *   <li>Status dos appenders (started/stopped)</li>
 * </ul>
 * <p>
 * Limiares:
 * <ul>
 *   <li>&lt; 80% da fila ocupada → UP</li>
 *   <li>80–95% → UP (com warning)</li>
 *   <li>&gt; 95% → DOWN (backpressure critica)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class LoggingHealthIndicator implements HealthIndicator {

  private static final double WARNING_THRESHOLD = 0.80;
  private static final double CRITICAL_THRESHOLD = 0.95;

  @Override
  public Health health() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
      return Health.unknown().withDetail("reason", "LoggerFactory is not Logback").build();
    }

    Map<String, Object> details = new LinkedHashMap<>();
    Health.Builder healthBuilder = Health.up();

    ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(
        ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

    Iterator<Appender<ch.qos.logback.classic.spi.ILoggingEvent>> appenders = rootLogger.iteratorForAppenders();

    boolean hasAsync = false;
    while (appenders.hasNext()) {
      Appender<?> appender = appenders.next();

      if (appender instanceof AsyncAppender asyncAppender) {
        hasAsync = true;
        int queueSize = asyncAppender.getQueueSize();
        int remainingCapacity = asyncAppender.getRemainingCapacity();
        int used = queueSize - remainingCapacity;
        double utilization = queueSize > 0 ? (double) used / queueSize : 0;

        Map<String, Object> asyncDetails = new LinkedHashMap<>();
        asyncDetails.put("queueSize", queueSize);
        asyncDetails.put("used", used);
        asyncDetails.put("remainingCapacity", remainingCapacity);
        asyncDetails.put("utilization", String.format("%.1f%%", utilization * 100));
        asyncDetails.put("discardingThreshold", asyncAppender.getDiscardingThreshold());
        asyncDetails.put("started", asyncAppender.isStarted());

        details.put("async." + asyncAppender.getName(), asyncDetails);

        if (utilization > CRITICAL_THRESHOLD) {
          healthBuilder = Health.down();
          details.put("warning", "Async appender queue utilization is critical (>" +
              (int)(CRITICAL_THRESHOLD * 100) + "%)");
        } else if (utilization > WARNING_THRESHOLD) {
          details.put("warning", "Async appender queue utilization is high (>" +
              (int)(WARNING_THRESHOLD * 100) + "%)");
        }
      } else {
        details.put("appender." + appender.getName(), Map.of(
            "type", appender.getClass().getSimpleName(),
            "started", appender.isStarted()
        ));
      }
    }

    if (!hasAsync) {
      details.put("async", "No async appender detected (synchronous logging)");
    }

    details.put("loggerCount", loggerContext.getLoggerList().size());

    return healthBuilder.withDetails(details).build();
  }
}
