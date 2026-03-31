package br.com.ocoelhogabriel.observability.context;

import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;

/**
 * Gerencia o {@link CorrelationContext} na thread atual usando {@link InheritableThreadLocal},
 * garantindo propagação automática para threads filhas criadas diretamente.
 * <p>
 * Para pools de threads (como {@code @Async} ou {@code CompletableFuture}), use
 * {@link ContextPropagator} como {@code TaskDecorator}.
 * <p>
 * Padrão de uso:
 * <pre>{@code
 * CorrelationContextHolder.set(context);
 * try {
 *     // ... lógica de negócio ...
 * } finally {
 *     CorrelationContextHolder.clear();
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class CorrelationContextHolder {

  private static final InheritableThreadLocal<CorrelationContext> CONTEXT =
      new InheritableThreadLocal<>();

  private CorrelationContextHolder() {}

  /**
   * Define o contexto de correlação para a thread atual e o sincroniza com o MDC.
   */
  public static void set(CorrelationContext context) {
    if (context == null) {
      clear();
      return;
    }
    CONTEXT.set(context);
    syncToMdc(context);
  }

  /**
   * Obtém o contexto atual. Se nenhum foi definido, retorna um contexto vazio
   * (nunca retorna null).
   */
  public static CorrelationContext get() {
    CorrelationContext ctx = CONTEXT.get();
    return ctx != null ? ctx : CorrelationContext.empty();
  }

  /**
   * Obtém o contexto atual como Optional (vazio se nenhum foi definido explicitamente).
   */
  public static Optional<CorrelationContext> current() {
    return Optional.ofNullable(CONTEXT.get());
  }

  /**
   * Verifica se existe um contexto definido na thread atual.
   */
  public static boolean isPresent() {
    return CONTEXT.get() != null;
  }

  /**
   * Limpa o contexto da thread atual e remove as chaves do MDC.
   */
  public static void clear() {
    CorrelationContext ctx = CONTEXT.get();
    if (ctx != null) {
      clearMdc(ctx);
    }
    CONTEXT.remove();
  }

  /**
   * Captura um snapshot do contexto atual para propagação manual.
   * Usado internamente pelo {@link ContextPropagator}.
   *
   * @return snapshot do contexto, ou {@link CorrelationContext#empty()} se ausente
   */
  public static CorrelationContext snapshot() {
    return get();
  }

  /**
   * Restaura um snapshot previamente capturado, criando um child span.
   * Usado internamente pelo {@link ContextPropagator}.
   */
  public static void restore(CorrelationContext snapshot) {
    if (snapshot != null) {
      set(snapshot.createChildSpan());
    }
  }

  /**
   * Atualiza parcialmente o contexto atual sem substituí-lo inteiro.
   * Útil para adicionar informações que chegam em momentos diferentes
   * (ex.: tenantId só fica disponível após a autenticação).
   */
  public static void enrich(java.util.function.UnaryOperator<CorrelationContext.Builder> enricher) {
    CorrelationContext current = get();
    CorrelationContext enriched = enricher.apply(current.toBuilder()).build();
    set(enriched);
  }

  // ── MDC sync ─────────────────────────────────────────────────────

  private static void syncToMdc(CorrelationContext context) {
    Map<String, String> mdcMap = context.toMdcMap();
    mdcMap.forEach(MDC::put);
  }

  private static void clearMdc(CorrelationContext context) {
    context.toMdcMap().keySet().forEach(MDC::remove);
  }
}
