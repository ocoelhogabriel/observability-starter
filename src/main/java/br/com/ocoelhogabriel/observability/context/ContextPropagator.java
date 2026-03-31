package br.com.ocoelhogabriel.observability.context;

import org.springframework.core.task.TaskDecorator;

/**
 * {@link TaskDecorator} que propaga o {@link CorrelationContext} para threads
 * de pools gerenciados pelo Spring ({@code @Async}, {@code ThreadPoolTaskExecutor}, etc.).
 * <p>
 * Ao decorar uma task, captura um snapshot do contexto na thread chamadora e
 * o restaura (como child span) na thread do pool antes da execução,
 * limpando após a conclusão.
 * <p>
 * Registro automático via auto-configuration:
 * <pre>{@code
 * @Bean
 * public TaskDecorator contextPropagator() {
 *     return new ContextPropagator();
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class ContextPropagator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    // Captura na thread chamadora
    CorrelationContext snapshot = CorrelationContextHolder.snapshot();

    return () -> {
      try {
        CorrelationContextHolder.restore(snapshot);
        runnable.run();
      } finally {
        CorrelationContextHolder.clear();
      }
    };
  }
}
