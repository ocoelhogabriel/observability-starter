package br.com.ocoelhogabriel.observability.sampling;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Marker;

/**
 * Logback {@link TurboFilter} que realiza sampling probabilístico
 * em logs de nível DEBUG e TRACE em ambientes de produção.
 * <p>
 * Em sistemas de alto throughput, logs DEBUG/TRACE podem gerar volume
 * excessivo. Este filtro aceita apenas uma porcentagem configurável
 * desses logs, reduzindo drasticamente o I/O sem perder visibilidade
 * estatística da saúde do sistema.
 * <p>
 * Logs de nível INFO, WARN e ERROR nunca são filtrados.
 * <p>
 * <b>Configuração:</b>
 * <pre>{@code
 * ocoelhogabriel:
 *   observability:
 *     sampling:
 *       enabled: true
 *       debug-rate: 0.1   # aceita 10% dos DEBUG
 *       trace-rate: 0.01  # aceita 1% dos TRACE
 * }</pre>
 *
 * @since 1.0.0
 */
public class LogSampler extends TurboFilter {

  private double debugRate = 0.1;
  private double traceRate = 0.01;

  public void setDebugRate(double debugRate) {
    this.debugRate = Math.max(0.0, Math.min(1.0, debugRate));
  }

  public void setTraceRate(double traceRate) {
    this.traceRate = Math.max(0.0, Math.min(1.0, traceRate));
  }

  public double getDebugRate() {
    return debugRate;
  }

  public double getTraceRate() {
    return traceRate;
  }

  @Override
  public FilterReply decide(Marker marker, Logger logger, Level level,
                             String format, Object[] params, Throwable t) {
    if (level == null) {
      return FilterReply.NEUTRAL;
    }

    // INFO, WARN, ERROR — nunca filtrar
    if (level.isGreaterOrEqual(Level.INFO)) {
      return FilterReply.NEUTRAL;
    }

    // DEBUG
    if (level == Level.DEBUG) {
      return shouldAccept(debugRate) ? FilterReply.NEUTRAL : FilterReply.DENY;
    }

    // TRACE
    if (level == Level.TRACE) {
      return shouldAccept(traceRate) ? FilterReply.NEUTRAL : FilterReply.DENY;
    }

    return FilterReply.NEUTRAL;
  }

  private boolean shouldAccept(double rate) {
    if (rate >= 1.0) return true;
    if (rate <= 0.0) return false;
    return ThreadLocalRandom.current().nextDouble() < rate;
  }
}
