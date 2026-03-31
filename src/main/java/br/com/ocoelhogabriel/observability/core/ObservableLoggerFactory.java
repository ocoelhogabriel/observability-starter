package br.com.ocoelhogabriel.observability.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory para criação de instâncias de {@link ObservableLogger}.
 * <p>
 * Mantém um cache interno para evitar criação duplicada de loggers
 * para a mesma classe.
 * <p>
 * <b>Uso:</b>
 * <pre>{@code
 * private static final ObservableLogger log = ObservableLoggerFactory.getLogger(MyService.class);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class ObservableLoggerFactory {

  private static final Map<String, ObservableLogger> CACHE = new ConcurrentHashMap<>();

  private ObservableLoggerFactory() {}

  /**
   * Obtém um logger para a classe especificada. Loggers são cacheados por nome.
   */
  public static ObservableLogger getLogger(Class<?> clazz) {
    return CACHE.computeIfAbsent(clazz.getName(), name -> new ObservableLogger(clazz));
  }

  /**
   * Obtém um logger pelo nome. Loggers são cacheados por nome.
   */
  public static ObservableLogger getLogger(String name) {
    return CACHE.computeIfAbsent(name, n -> new ObservableLogger(n));
  }
}
