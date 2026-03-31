package br.com.ocoelhogabriel.observability.masking;

import java.util.regex.Pattern;

/**
 * Define um pattern de masking para dados sensíveis.
 * <p>
 * Cada pattern tem um nome descritivo, uma regex de detecção e o
 * texto de substituição.
 * <p>
 * Patterns são configurados via {@code ocoelhogabriel.observability.masking.patterns}
 * no application.yaml ou programaticamente.
 *
 * @param name        Nome descritivo (ex.: "cpf", "email", "bearer-token")
 * @param regex       Regex que detecta o dado sensível
 * @param replacement Texto de substituição (ex.: "***.***.***-**")
 *
 * @since 1.0.0
 */
public record MaskingPattern(String name, Pattern regex, String replacement) {

  public MaskingPattern(String name, String regex, String replacement) {
    this(name, Pattern.compile(regex), replacement);
  }

  /**
   * Aplica este pattern à mensagem, substituindo todas as ocorrências.
   */
  public String apply(String input) {
    if (input == null) return null;
    return regex.matcher(input).replaceAll(replacement);
  }
}
