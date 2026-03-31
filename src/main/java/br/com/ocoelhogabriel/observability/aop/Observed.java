package br.com.ocoelhogabriel.observability.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um método para instrumentação automática de observabilidade.
 * <p>
 * Quando aplicada, o {@link ObservedAspect} gera automaticamente:
 * <ul>
 *   <li>Log de entrada: {@code ▶ [operationName] args=[...]}</li>
 *   <li>Log de saída: {@code ◀ [operationName] duration=142ms result=OK}</li>
 *   <li>Log de erro: {@code ✖ [operationName] duration=15ms error=...}</li>
 *   <li>Timer Micrometer: {@code ocoelhogabriel.observed.duration{class=..., method=...}}</li>
 * </ul>
 * <p>
 * <b>Exemplo:</b>
 * <pre>{@code
 * @Observed(operation = "createUser", logArgs = true)
 * public User createUser(CreateUserCommand cmd) {
 *     // ...
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Observed {

  /**
   * Nome da operação. Se vazio, usa {@code ClassName.methodName}.
   */
  String operation() default "";

  /**
   * Se deve logar os argumentos do método.
   * CUIDADO: não ative para métodos que recebem dados sensíveis
   * (passwords, tokens, etc.) a menos que o masking esteja ativo.
   */
  boolean logArgs() default false;

  /**
   * Se deve logar o resultado do método (toString do retorno).
   */
  boolean logResult() default false;

  /**
   * Nível de log para entrada/saída normais. Default: DEBUG.
   * Erros sempre são logados em ERROR.
   */
  LogLevel level() default LogLevel.DEBUG;

  enum LogLevel {
    TRACE, DEBUG, INFO
  }
}
