package br.com.ocoelhogabriel.observability.aop;

import br.com.ocoelhogabriel.observability.core.ObservableLogger;
import br.com.ocoelhogabriel.observability.core.ObservableLoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * Aspect que intercepta métodos anotados com {@link Observed} e automaticamente:
 * <ul>
 *   <li>Loga entrada e saída do método</li>
 *   <li>Mede duração e emite timer no Micrometer</li>
 *   <li>Loga exceções com stack trace completo</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Aspect
public class ObservedAspect {

  private final MeterRegistry meterRegistry;

  public ObservedAspect(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Around("@annotation(observed)")
  public Object observe(ProceedingJoinPoint joinPoint, Observed observed) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Class<?> targetClass = joinPoint.getTarget().getClass();
    String methodName = signature.getMethod().getName();

    String operationName = observed.operation().isEmpty()
        ? targetClass.getSimpleName() + "." + methodName
        : observed.operation();

    ObservableLogger log = ObservableLoggerFactory.getLogger(targetClass);

    // ── Log de entrada ────────────────────────────────────────────
    if (observed.logArgs()) {
      String argsStr = Arrays.toString(joinPoint.getArgs());
      logAtLevel(log, observed.level(), "▶ [{}] args={}", operationName, argsStr);
    } else {
      logAtLevel(log, observed.level(), "▶ [{}]", operationName);
    }

    long startNanos = System.nanoTime();
    String outcome = "SUCCESS";
    long durationNanos = 0;

    try {
      Object result = joinPoint.proceed();
      durationNanos = System.nanoTime() - startNanos;
      long durationMs = durationNanos / 1_000_000;

      // ── Log de saída ──────────────────────────────────────────
      if (observed.logResult() && result != null) {
        logAtLevel(log, observed.level(),
            "◀ [{}] duration={}ms result={}", operationName, durationMs, result);
      } else {
        logAtLevel(log, observed.level(),
            "◀ [{}] duration={}ms", operationName, durationMs);
      }

      return result;

    } catch (Throwable t) {
      durationNanos = System.nanoTime() - startNanos;
      long durationMs = durationNanos / 1_000_000;
      outcome = "ERROR";

      log.operation(operationName)
          .durationMs(durationMs)
          .outcome("ERROR")
          .field("errorType", t.getClass().getSimpleName())
          .error("✖ [{}] duration={}ms error={}", operationName, durationMs, t.getMessage());

      throw t;
    } finally {
      // durationNanos já foi capturado no try ou no catch — o finally não remede o clock
      recordTimer(targetClass, methodName, operationName, outcome, durationNanos);
    }
  }

  private void logAtLevel(ObservableLogger log, Observed.LogLevel level,
                           String message, Object... args) {
    switch (level) {
      case TRACE -> log.trace(message, args);
      case DEBUG -> log.debug(message, args);
      case INFO  -> log.info(message, args);
    }
  }

  private void recordTimer(Class<?> targetClass, String methodName,
                            String operationName, String outcome, long durationNanos) {
    if (meterRegistry == null) return;

    Timer.builder("ocoelhogabriel.observed.duration")
        .description("Duration of @Observed methods")
        .tag("class", targetClass.getSimpleName())
        .tag("method", methodName)
        .tag("operation", operationName)
        .tag("outcome", outcome)
        .register(meterRegistry)
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }
}
