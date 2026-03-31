package br.com.ocoelhogabriel.observability.core;

import br.com.ocoelhogabriel.observability.masking.DataMasker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger de alta performance com auto-detecção de método chamador,
 * API fluente, integração com Micrometer, masking automático de dados sensíveis
 * e enrichment via
 * {@link br.com.ocoelhogabriel.observability.context.CorrelationContext}.
 * <p>
 * <b>Diferencial:</b> O nome da função/método é <b>opcional</b>. Se o
 * desenvolvedor
 * não informar, o logger detecta automaticamente qual método chamou o log
 * usando
 * {@link StackWalker} (Java 21), eliminando código boilerplate.
 * <p>
 * <b>Uso básico (auto-detecção — o mais simples):</b>
 * 
 * <pre>{@code
 * private static final ObservableLogger log = ObservableLoggerFactory.getLogger(MyService.class);
 *
 * public void createUser(String name) {
 *   log.info("Usuário {} criado", name);
 *   // Output: Fn[createUser] → Usuário Gabriel criado
 * }
 * }</pre>
 *
 * <b>Uso com nome explícito (quando quiser sobrescrever):</b>
 * 
 * <pre>{@code
 * log.info("CUSTOM_OP", "Processando pagamento de {}", valor);
 * // Output: Fn[CUSTOM_OP] → Processando pagamento de 150.00
 * }</pre>
 *
 * <b>Uso avançado (Fluent API):</b>
 * 
 * <pre>{@code
 * log.operation("CREATE_USER")
 *     .tenant(tenantId)
 *     .entity("User", userId)
 *     .info("Usuário criado com sucesso em {}ms", duration);
 * }</pre>
 *
 * @since 1.0.0
 */
public class ObservableLogger {

  private final Logger logger;
  private static final String FUNCTION_MESSAGE = "Fn[%s] → %s";

  /**
   * StackWalker singleton — muito mais performático que Thread.getStackTrace().
   * Retém referência à classe para filtrar frames internos da lib.
   */
  private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

  /** Classes internas cujos frames devem ser ignorados na resolução do caller. */
  private static final Set<String> INTERNAL_CLASSES = Set.of(
      ObservableLogger.class.getName(),
      LogEvent.class.getName(),
      ObservableLoggerFactory.class.getName());

  // ── Métricas (lazy-init, thread-safe) ─────────────────────────────
  private static volatile MeterRegistry meterRegistry;
  private static volatile DataMasker dataMasker;
  private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();

  // ── Construção ────────────────────────────────────────────────────

  ObservableLogger(Class<?> clazz) {
    this.logger = LoggerFactory.getLogger(clazz);
  }

  ObservableLogger(String name) {
    this.logger = LoggerFactory.getLogger(name);
  }

  // ── Configuração global (chamado pelo auto-configuration) ─────────

  /** Registra o MeterRegistry global. Chamado pelo auto-configuration. */
  public static void setMeterRegistry(MeterRegistry registry) {
    meterRegistry = registry;
  }

  /** Registra o DataMasker global. Chamado pelo auto-configuration. */
  public static void setDataMasker(DataMasker masker) {
    dataMasker = masker;
  }

  // ── Fluent API ────────────────────────────────────────────────────

  /**
   * Inicia um log event com uma operação nomeada (ex.: "CREATE_USER",
   * "PROCESS_PAYMENT").
   * Retorna um {@link LogEvent} builder fluente.
   */
  public LogEvent operation(String operationName) {
    return new LogEvent(this, operationName);
  }

  // ════════════════════════════════════════════════════════════════════
  // API SEM nome de função — AUTO-DETECTA o método chamador
  // ════════════════════════════════════════════════════════════════════

  public void info(String message, Object... args) {
    String caller = resolveCallerMethod();
    logger.info(mask(formatFunction(caller, message)), args);
    incrementCounter("INFO");
  }

  public void warn(String message, Object... args) {
    String caller = resolveCallerMethod();
    logger.warn(mask(formatFunction(caller, message)), args);
    incrementCounter("WARN");
  }

  public void error(String message, Object... args) {
    String caller = resolveCallerMethod();
    logger.error(mask(formatFunction(caller, message)), args);
    incrementCounter("ERROR");
  }

  public void error(String message, Throwable t, Object... args) {
    String caller = resolveCallerMethod();
    String formatted = mask(formatFunction(caller, message));
    if (args.length == 0) {
      logger.error(formatted, t);
    } else {
      Object[] combined = appendThrowable(args, t);
      logger.error(formatted, combined);
    }
    incrementCounter("ERROR");
  }

  public void debug(String message, Object... args) {
    if (logger.isDebugEnabled()) {
      String caller = resolveCallerMethod();
      logger.debug(mask(formatFunction(caller, message)), args);
    }
  }

  public void trace(String message, Object... args) {
    if (logger.isTraceEnabled()) {
      String caller = resolveCallerMethod();
      logger.trace(mask(formatFunction(caller, message)), args);
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // API COM nome de função explícito — quando o dev quer sobrescrever
  // ════════════════════════════════════════════════════════════════════

  public void info(String func, String message, Object... args) {
    logger.info(mask(formatFunction(func, message)), args);
    incrementCounter("INFO");
  }

  public void warn(String func, String message, Object... args) {
    logger.warn(mask(formatFunction(func, message)), args);
    incrementCounter("WARN");
  }

  public void error(String func, String message, Object... args) {
    logger.error(mask(formatFunction(func, message)), args);
    incrementCounter("ERROR");
  }

  public void error(String func, String message, Throwable t, Object... args) {
    String formatted = mask(formatFunction(func, message));
    if (args.length == 0) {
      logger.error(formatted, t);
    } else {
      Object[] combined = appendThrowable(args, t);
      logger.error(formatted, combined);
    }
    incrementCounter("ERROR");
  }

  public void debug(String func, String message, Object... args) {
    if (logger.isDebugEnabled()) {
      logger.debug(mask(formatFunction(func, message)), args);
    }
  }

  public void trace(String func, String message, Object... args) {
    if (logger.isTraceEnabled()) {
      logger.trace(mask(formatFunction(func, message)), args);
    }
  }

  // ── API para exceções (auto-detecta caller) ───────────────────────

  public void error(Throwable t) {
    String caller = resolveCallerMethod();
    logger.error(mask(formatFunction(caller, "Erro inesperado: {}")), t.getMessage(), t);
    incrementCounter("ERROR");
  }

  public void warn(Throwable t) {
    String caller = resolveCallerMethod();
    logger.warn(mask(formatFunction(caller, "Aviso: {}")), t.getMessage(), t);
    incrementCounter("WARN");
  }

  public void debug(Throwable t) {
    if (logger.isDebugEnabled()) {
      String caller = resolveCallerMethod();
      logger.debug(mask(formatFunction(caller, "Debug: {}")), t.getMessage(), t);
    }
  }

  // ── Level checks ──────────────────────────────────────────────────

  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
  }

  public boolean isErrorEnabled() {
    return logger.isErrorEnabled();
  }

  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  // ── Métodos internos (acessíveis pelo LogEvent) ───────────────────

  Logger delegate() {
    return logger;
  }

  String mask(String message) {
    DataMasker m = dataMasker;
    return m != null ? m.mask(message) : message;
  }

  void incrementCounter(String level) {
    MeterRegistry reg = meterRegistry;
    if (reg == null)
      return;

    String loggerName = logger.getName();
    String key = level + ":" + loggerName;

    COUNTERS.computeIfAbsent(key, k -> Counter.builder("ocoelhogabriel.log.events")
        .description("Log events emitted by the application")
        .tag("level", level)
        .tag("logger", loggerName)
        .register(reg)).increment();
  }

  // ══════════════════════════════════════════════════════════════════
  // RESOLUÇÃO AUTOMÁTICA DO MÉTODO CHAMADOR (StackWalker — Java 9+)
  // ══════════════════════════════════════════════════════════════════

  /**
   * Usa {@link StackWalker} para descobrir qual método de negócio chamou o
   * logger.
   * <p>
   * Ignora todos os frames internos do próprio ObservableLogger, LogEvent e
   * Factory.
   * Retorna o {@code methodName} do primeiro frame externo encontrado.
   * <p>
   * <b>Performance:</b> StackWalker é significativamente mais rápido que
   * {@code Thread.currentThread().getStackTrace()} pois permite lazy walking
   * e early termination — paramos no primeiro frame relevante.
   */
  private static String resolveCallerMethod() {
    return STACK_WALKER.walk(frames -> frames
        .filter(frame -> !INTERNAL_CLASSES.contains(frame.getClassName()))
        .findFirst()
        .map(StackWalker.StackFrame::getMethodName)
        .orElse("unknown"));
  }

  // ── Utilidades ────────────────────────────────────────────────────

  private String formatFunction(String function, String message) {
    return FUNCTION_MESSAGE.formatted(function, message);
  }

  private Object[] appendThrowable(Object[] args, Throwable t) {
    Object[] combined = new Object[args.length + 1];
    System.arraycopy(args, 0, combined, 0, args.length);
    combined[args.length] = t;
    return combined;
  }
}
